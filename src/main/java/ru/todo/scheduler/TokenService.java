package ru.todo.scheduler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private final String password;

    public TokenService(@Value("${todo.password:}") String password) {
        this.password = password;
    }

    public boolean enabled() {
        return !password.isEmpty();
    }

    public boolean passwordMatches(String candidate) {
        return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                (candidate == null ? "" : candidate).getBytes(StandardCharsets.UTF_8));
    }

    public String issue() {
        if (!enabled()) {
            return "";
        }
        String content = encode(HEADER) + "." + encode(payload());
        return content + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(content));
    }

    public boolean valid(String token) {
        if (!enabled() || token == null) {
            return false;
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return false;
        }
        try {
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            return HEADER.equals(header) && payload().equals(claims)
                    && MessageDigest.isEqual(signature, sign(parts[0] + "." + parts[1]));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String payload() {
        return "{\"hash\":\"" + hex(sha256(password.getBytes(StandardCharsets.UTF_8))) + "\"}";
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot sign token", exception);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String hex(byte[] input) {
        StringBuilder builder = new StringBuilder(input.length * 2);
        for (byte value : input) {
            builder.append(Character.forDigit((value >>> 4) & 15, 16));
            builder.append(Character.forDigit(value & 15, 16));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
