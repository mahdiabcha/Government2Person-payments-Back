package com.mini.g2p.profile.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class SecurityHelpers {
  private static final ObjectMapper M = new ObjectMapper();
  private SecurityHelpers() {}

  public static String currentUser(HttpHeaders headers){
    // Prefer explicit header (our gateway/interceptor sets it)
    String u = headers.getFirst("X-Auth-User");
    if (u != null && !u.isBlank()) return u;

    // Fallback: parse JWT payload
    String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
    if (auth != null && auth.startsWith("Bearer ")) {
      String[] p = auth.substring(7).split("\\.");
      if (p.length >= 2) {
        try {
          String json = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);
          JsonNode n = M.readTree(json);
          if (n.has("sub")) {
            return n.get("sub").asText("");
          }
        } catch (Exception ignored) {}
      }
    }
    return null;
  }

  public static boolean hasRole(HttpHeaders h, String want){
    String rh = h.getFirst("X-Auth-Roles");
    if (rh != null) {
      for (String r : rh.split("[,\\s]+")) {
        if (want.equalsIgnoreCase(r.replace("ROLE_", ""))) return true;
      }
    }
    String auth = h.getFirst(HttpHeaders.AUTHORIZATION);
    if (auth != null && auth.startsWith("Bearer ")) {
      String[] p = auth.substring(7).split("\\.");
      if (p.length >= 2) try{
        String json = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);
        JsonNode n = M.readTree(json);
        if (n.has("roles")) for (JsonNode r : n.get("roles")) {
          if (want.equalsIgnoreCase(r.asText("").replace("ROLE_", ""))) return true;
        }
      } catch (Exception ignored) {}
    }
    return false;
  }
}
