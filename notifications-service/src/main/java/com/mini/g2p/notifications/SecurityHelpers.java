package com.mini.g2p.notifications;

import org.springframework.http.HttpHeaders;

public final class SecurityHelpers {
  private SecurityHelpers(){}

  public static String username(HttpHeaders h){
    String u = h.getFirst("X-Auth-User");
    if (u==null || u.isBlank()) throw new RuntimeException("Missing X-Auth-User");
    return u;
  }
  public static boolean isAdmin(HttpHeaders h){
    String r = h.getFirst("X-Auth-Roles");
    if (r==null) return false;
    for (String s : r.split("[,\\s]+")) if ("ADMIN".equalsIgnoreCase(s) || "ROLE_ADMIN".equalsIgnoreCase(s)) return true;
    return false;
  }
}
