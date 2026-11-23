# ChainWatch 부하 테스트

조회 트래픽의 TPS, 응답 시간, 오류율을 k6와 JMeter로 측정한다. 기본 대상은 로컬 백엔드이며 데이터 변경, 블록 수집, AI 분석 API는 호출하지 않는다.

최근 측정 결과는 [2026-11-23 테스트 보고서](REPORT-2026-11-23.md)에 정리되어 있다.

## 실행

먼저 백엔드를 실행한 뒤 저장소 루트에서 시작한다.

```bash
mkdir -p load-tests/results
k6 run --summary-export load-tests/results/smoke.json load-tests/k6/api-read.js

PROFILE=load TARGET_RPS=50 DURATION=2m \
  k6 run --summary-export load-tests/results/load-50.json load-tests/k6/api-read.js

PROFILE=tps TARGET_RPS=100 DURATION=2m \
  k6 run --summary-export load-tests/results/tps-100.json load-tests/k6/api-read.js
```

JWT가 켜진 환경은 `AUTH_MODE=demo` 또는 `USERNAME`/`PASSWORD`를 전달한다. 이미 발급한 토큰은 `ACCESS_TOKEN`으로 전달할 수 있다.

```bash
BASE_URL=https://example.com AUTH_MODE=demo DEMO_ROLE=VIEWER \
  PROFILE=tps TARGET_RPS=20 DURATION=1m k6 run load-tests/k6/api-read.js
```

공통 합격 기준은 오류율 1% 미만, p95 500ms 미만, p99 1초 미만, dropped iteration 0건이다. 목표 TPS는 `http_reqs` rate가 `TARGET_RPS`에 도달했는지 함께 확인한다. 도달하지 못하고 `dropped_iterations`가 생기면 `PRE_ALLOCATED_VUS`/`MAX_VUS`를 늘리거나 시스템 병목을 먼저 확인한다.

운영 서비스에는 낮은 TPS부터 올리고, 쓰기·수집·AI 시나리오는 비용과 데이터 변경을 승인한 별도 환경에서만 수행한다.

## JMeter

k6와 같은 가중치로 5개 조회 API를 호출하며, 목표 TPS는 전체 스레드에 공유되는 Constant Throughput Timer로 제한한다.

```bash
mkdir -p load-tests/results/jmeter-100
TOKEN=$(curl -sS -H 'Content-Type: application/json' \
  -d '{"role":"VIEWER"}' http://127.0.0.1:18080/api/auth/demo | jq -r .accessToken)
jmeter -n -t load-tests/jmeter/api-read.jmx \
  -Jhost=127.0.0.1 -Jport=18080 -Jtps=100 \
  -Jtoken="$TOKEN" \
  -Jthreads=100 -Jramp=10 -Jduration=60 \
  -l load-tests/results/jmeter-100.jtl \
  -e -o load-tests/results/jmeter-100
```

CLI 비-GUI 모드로 실행해야 부하 발생기 자체의 오버헤드를 줄일 수 있다. JWT가 활성화된 대상은 위 예시처럼 사전에 발급한 토큰을 `-Jtoken`으로 전달한다.
