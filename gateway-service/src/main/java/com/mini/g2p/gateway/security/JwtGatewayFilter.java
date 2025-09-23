package com.mini.g2p.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

  private final JwtProps props;
  private final JwtUtil jwt;
  private final AntPathMatcher matcher = new AntPathMatcher();

  @Autowired
  public JwtGatewayFilter(JwtProps props, JwtUtil jwt) {
    this.props = props;
    this.jwt = jwt;
  }

  @Override
  public int getOrder() {
    // Run early, before routing
    return -100;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
    var request = exchange.getRequest();

    // 1) Always let preflight pass (CORS)
    if (request.getMethod() == HttpMethod.OPTIONS) {
      return chain.filter(exchange);
    }

    // 2) Allow public paths without JWT
    final String path = request.getURI().getPath();
    boolean isPublic = props.getPublicPaths().stream().anyMatch(p -> matcher.match(p, path));
    if (isPublic) {
      return chain.filter(exchange);
    }

    // 3) Require and validate JWT
    String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (auth == null || !auth.startsWith("Bearer ")) {
      return unauthorized(exchange, "Missing Bearer token");
    }

    String token = auth.substring("Bearer ".length()).trim();
    JwtUtil.Decoded d;
    try {
      d = jwt.decode(token); // verifies signature & extracts sub/roles
    } catch (Exception ex) {
      return unauthorized(exchange, "Invalid token");
    }

    // 4) Propagate identity to downstream services
    String rolesCsv = String.join(",", d.roles());
    var mutated = exchange.mutate()
        .request(builder -> builder.headers(h -> {
          h.set("X-Auth-User", d.username());
          h.set("X-Auth-Roles", rolesCsv);
        }))
        .build();

    ServerWebExchangeUtils.putUriTemplateVariables(mutated, ServerWebExchangeUtils.getUriTemplateVariables(exchange));
    return chain.filter(mutated);
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
    var response = exchange.getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    byte[] bytes = ("{\"error\":\"unauthorized\",\"message\":\"" + message + "\"}")
        .getBytes(StandardCharsets.UTF_8);
    var buffer = response.bufferFactory().wrap(bytes);
    response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
    return response.writeWith(Mono.just(buffer));
  }
}
