package com.mini.g2p.programcatalog.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Component
public class NotificationsClient {

  private final RestTemplate rest;
  private final String internalKey;

  public NotificationsClient(
      @Value("${app.notifications.url:http://notifications-service:8087}") String baseUrl,
      @Value("${app.notifications.internal-key:a6f3d8c2b7944f12a17d05c6b9f82a7d3e1f74c5a94e89b15b3f0c7e1a2qa7o2}") String internalKey,
      RestTemplateBuilder builder) {
    this.rest = builder
        .rootUri(baseUrl)
        .setConnectTimeout(Duration.ofSeconds(2))
        .setReadTimeout(Duration.ofSeconds(3))
        .build();
    this.internalKey = internalKey;
  }

  private void post(Map<String, Object> payload) {
    try {
      HttpHeaders h = new HttpHeaders();
      h.setContentType(MediaType.APPLICATION_JSON);
      h.set("X-Internal-Key", internalKey);
      HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, h);
      rest.postForEntity("/notifications/events", req, Void.class);
    } catch (Exception e) {
      // Fire-and-forget (don't break business flow if notifications are down)
    }
  }

  // Convenience helpers:
  public void programActivated(Long programId, String programName) {
    post(Map.of("type", "program.activated", "programId", programId, "programName", programName, "broadcast", true));
  }

  public void enrollmentRequested(Long programId, String programName, String applicantUsername) {
    post(Map.of("type", "enrollment.requested", "programId", programId, "programName", programName,
        "applicantUsername", applicantUsername));
  }

  public void enrollmentStatusChanged(Long enrollmentId, Long programId, String citizenUsername, String status, String note) {
    post(Map.of("type", "enrollment.status.changed", "enrollmentId", enrollmentId, "programId", programId,
        "citizenUsername", citizenUsername, "status", status, "note", note == null ? "" : note));
  }

  public void paymentSucceeded(Long instructionId, Long batchId, Long programId, Long cycleId,
                               String payeeUsername, Object amount, String currency, String bankRef) {
    post(Map.of("type", "payment.succeeded",
        "instructionId", instructionId, "batchId", batchId, "programId", programId,
        "cycleId", cycleId, "payeeUsername", payeeUsername, "amount", amount, "currency", currency,
        "bankRef", bankRef == null ? "" : bankRef));
  }

  public void paymentBatchCompleted(Long batchId, Long programId, int success, int failed, int total) {
    post(Map.of("type", "payment.batch.completed", "batchId", batchId, "programId", programId,
        "counts", Map.of("success", success, "failed", failed, "total", total)));
  }
}
