package com.chainwatch.backend.user.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 비교용 HMAC 지문. 인증에는 절대로 사용하지 않는다. */
@Component
public class PasswordFingerprintService {
    private final SecretKeySpec key;

    public PasswordFingerprintService(@Value("${chainwatch.security.password-pepper}") String pepper) {
        if (pepper == null || pepper.length() < 32) {
            throw new IllegalArgumentException("CHAINWATCH_PASSWORD_PEPPER must contain at least 32 characters");
        }
        this.key = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String fingerprint(String password) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }
}
