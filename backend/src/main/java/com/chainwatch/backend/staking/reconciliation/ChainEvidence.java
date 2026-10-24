package com.chainwatch.backend.staking.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.io.ByteArrayOutputStream;

/** Bounded RPC collection. Evidence includes partial responses when reconciliation fails. */
final class ChainEvidence {
    static class Failure extends RuntimeException { Failure(String code) { super(code); } }
    final ArrayNode raw;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Duration timeout;
    private final long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
    private int bytes;
    ChainEvidence(ObjectMapper mapper, Duration timeout) {
        this.mapper = mapper; this.timeout = timeout; this.raw = mapper.createArrayNode();
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }
    JsonNode beacon(URI base, String path, JsonNode body) { return request(URI.create(base.toString().replaceAll("/+$", "") + path), path, body); }
    JsonNode rpc(URI url, String method, JsonNode params) {
        var body = mapper.createObjectNode().put("jsonrpc", "2.0").put("id", 1).put("method", method);
        body.set("params", params);
        JsonNode response = request(url, method + ":" + params, body);
        require(response.path("id").isIntegralNumber() && response.path("id").asInt() == 1
                && "2.0".equals(response.path("jsonrpc").asText()), "INVALID_RPC_ENVELOPE");
        require(!response.has("error") && response.hasNonNull("result"), "EXECUTION_RPC_ERROR");
        return response.get("result");
    }
    private JsonNode request(URI url, String label, JsonNode body) {
        require(System.nanoTime() < deadline, "RECONCILIATION_TIMEOUT");
        var entry = raw.addObject().put("request", label);
        try {
            var b = HttpRequest.newBuilder(url).timeout(timeout).header("Accept", "application/json");
            if (body != null) b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            var future = http.sendAsync(b.build(), info -> new LimitedBody());
            HttpResponse<byte[]> response;
            try { response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (TimeoutException e) { future.cancel(true); throw new Failure("UPSTREAM_TIMEOUT"); }
            entry.put("httpStatus", response.statusCode());
            byte[] data = response.body();
            bytes += data.length;
            require(data.length <= 4_000_000 && bytes <= 24_000_000, "EVIDENCE_LIMIT");
            require(response.statusCode() == 200, "UPSTREAM_HTTP_" + response.statusCode());
            JsonNode node = mapper.readTree(data);
            require(node != null && node.isObject(), "INVALID_RESPONSE"); entry.set("response", node);
            return node;
        } catch (Failure e) { throw e;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new Failure("UPSTREAM_INTERRUPTED");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Failure f) throw f;
            throw new Failure(e.getCause() instanceof HttpTimeoutException ? "UPSTREAM_TIMEOUT" : "UPSTREAM_IO_ERROR");
        } catch (java.io.IOException e) { throw new Failure("UPSTREAM_IO_ERROR"); }
    }
    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription=s;s.request(1); }
        public void onNext(List<ByteBuffer> chunks) {
            for(ByteBuffer chunk:chunks) {
                if((long)output.size()+chunk.remaining()>4_000_000) {
                    subscription.cancel();result.completeExceptionally(new Failure("EVIDENCE_LIMIT"));return;
                }
                byte[] bytes=new byte[chunk.remaining()];chunk.get(bytes);output.writeBytes(bytes);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(output.toByteArray()); }
    }
    static void require(boolean ok, String code) { if (!ok) throw new Failure(code); }
    static String text(JsonNode n, String key) { require(n.path(key).isTextual(), "INVALID_RESPONSE"); return n.get(key).asText(); }
    static BigInteger uint(JsonNode n, String key) {
        String v = text(n, key); require(v.matches("0|[1-9][0-9]{0,19}"), "INVALID_UINT64");
        BigInteger r = new BigInteger(v); require(r.bitLength() <= 64, "INVALID_UINT64"); return r;
    }
    static BigInteger signed(JsonNode n, String key) {
        String v = text(n, key); require(v.matches("0|-?[1-9][0-9]{0,18}"), "INVALID_INT64");
        BigInteger r = new BigInteger(v); require(r.bitLength() <= 63, "INVALID_INT64"); return r;
    }
    static BigInteger hex(String v) { require(v.matches("0x(0|[1-9a-fA-F][0-9a-fA-F]*)"), "INVALID_HEX_QUANTITY"); return new BigInteger(v.substring(2), 16); }
    static String root(JsonNode n, String key) { String s = text(n, key); require(s.matches("0x[0-9a-fA-F]{64}"), "INVALID_ROOT"); return s; }
    static void verified(JsonNode n) {
        require(n.path("execution_optimistic").isBoolean() && !n.get("execution_optimistic").asBoolean(), "EXECUTION_OPTIMISTIC");
        require(n.path("finalized").isBoolean() && n.get("finalized").asBoolean(), "NOT_FINALIZED");
    }
    static JsonNode array(JsonNode n, String key) { require(n.path(key).isArray(), "MISSING_ARRAY_" + key.toUpperCase()); return n.get(key); }
}
