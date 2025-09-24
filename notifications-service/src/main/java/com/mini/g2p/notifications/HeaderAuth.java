package com.mini.g2p.notifications;

import org.springframework.http.HttpHeaders;
import java.util.Arrays;

public final class HeaderAuth {
  private HeaderAuth() {}

  public static String username(HttpHeaders headers) {
    String u = headers.getFirst("X-Auth-User");
    if (u == null || u.isBlank()) {
      // As a fallback (e.g., direct calls), also accept X-User
      u = headers.getFirst("X-User");
    }
    return u;
  }

  public static boolean isAdmin(HttpHeaders headers) {
    String roles = headers.getFirst("X-Auth-Roles");
    if (roles == null) return false;
    return Arrays.stream(roles.split(","))
        .map(String::trim)
        .anyMatch(r -> r.equalsIgnoreCase("ADMIN"));
  }
}
