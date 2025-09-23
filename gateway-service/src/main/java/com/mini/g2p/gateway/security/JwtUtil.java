package com.mini.g2p.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class JwtUtil {

  private final String secret;
  private final ObjectMapper mapper = new ObjectMapper();

  public static final class Decoded {
    private final String username;
    private final List<String> roles;

    public Decoded(String username, List<String> roles) {
      this.username = username;
      this.roles = roles;
    }
    public String username() { return username; }
    public List<String> roles() { return roles; }
  }

  public JwtUtil(@Value("${app.jwt.secret}") String secret) {
    this.secret = secret;
  }

  public Decoded decode(String token) throws Exception {
    String[] parts = token.split("\\.");
    if (parts.length != 3) throw new IllegalArgumentException("Malformed JWT");

    String headerB64 = parts[0];
    String payloadB64 = parts[1];
    String signatureB64 = parts[2];

    // Verify signature (HS256)
    String signingInput = headerB64 + "." + payloadB64;
    String expectedSig = base64UrlEncode(hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8),
        secret.getBytes(StandardCharsets.UTF_8)));
    if (!constantTimeEquals(signatureB64, expectedSig)) {
      throw new IllegalArgumentException("Bad signature");
    }

    // Parse payload
    byte[] payloadBytes = base64UrlDecode(payloadB64);
    JsonNode payload = mapper.readTree(payloadBytes);

    // Optional expiration check
    if (payload.has("exp") && payload.get("exp").canConvertToLong()) {
      long exp = payload.get("exp").asLong();
      if (Instant.now().getEpochSecond() > exp) {
        throw new IllegalArgumentException("Token expired");
      }
    }

    // Username from "username" or "sub"
    String username = null;
    if (payload.hasNonNull("username")) {
      username = payload.get("username").asText();
    } else if (payload.hasNonNull("sub")) {
      username = payload.get("sub").asText();
    }
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Missing subject");
    }

    // Roles from array or comma-separated string
    List<String> roles = new ArrayList<>();
    if (payload.has("roles")) {
      JsonNode r = payload.get("roles");
      if (r.isArray()) {
        r.forEach(n -> { if (n != null && !n.isNull()) roles.add(n.asText()); });
      } else if (r.isTextual()) {
        for (String s : r.asText().split(",")) {
          String t = s.trim();
          if (!t.isEmpty()) roles.add(t);
        }
      }
    } else if (payload.has("scope") && payload.get("scope").isTextual()) {
      for (String s : payload.get("scope").asText().split(" ")) {
        String t = s.trim();
        if (!t.isEmpty()) roles.add(t);
      }
    }

    return new Decoded(username, roles);
  }

  // --- helpers ---

  private static byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data);
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    if (a.length() != b.length()) return false;
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }

  private static byte[] base64UrlDecode(String s) {
    // URL-safe Base64 without padding
    String p = s.replace('-', '+').replace('_', '/');
    int pad = (4 - (p.length() % 4)) % 4;
    p = p + "====".substring(0, pad);
    return Base64.getDecoder().decode(p);
  }

  private static String base64UrlEncode(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
