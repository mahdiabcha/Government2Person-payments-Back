package com.mini.g2p.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/notifications")
public class NotificationsController {

  private final NotificationRepository repo;
  private final ObjectMapper objectMapper;
  private final String internalKey;

  public NotificationsController(NotificationRepository repo,
                                 ObjectMapper objectMapper,
                                 @Value("${app.internal-key:change-me}") String internalKey) {
    this.repo = repo;
    this.objectMapper = objectMapper;
    this.internalKey = internalKey;
  }

  // ========== Internal ingest (HTTP) ==========
  @PostMapping("/events")
  public ResponseEntity<?> ingest(@RequestHeader HttpHeaders headers,
                                  @RequestBody Map<String, Object> payload) throws JsonProcessingException {
    String key = headers.getFirst("X-Internal-Key");
    if (!Objects.equals(key, internalKey)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "invalid internal key"));
    }

    String type = String.valueOf(payload.getOrDefault("type", "")).trim();
    if (type.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "missing 'type'"));
    }

    Notification n = new Notification();
    n.setType(type);
    n.setStatus(NotificationStatus.NEW);
    n.setCreatedAt(Instant.now());
    n.setMetadataJson(objectMapper.writeValueAsString(payload));

    switch (type) {
      case "program.activated" -> {
        String programName = safeStr(payload.get("programName"));
        n.setTitle("New program is active");
        n.setBody(programName.isEmpty() ? "A program is now active." : (programName + " is now active."));
        n.setRecipientUsername(null);       // broadcast
        n.setAudienceRole(null);            // visible to all roles (CITIZEN & ADMIN)
      }
      case "enrollment.requested" -> {
        String applicant = safeStr(payload.get("applicantUsername"));
        String programName = safeStr(payload.get("programName"));
        n.setTitle("Enrollment request received");
        n.setBody((applicant.isEmpty() ? "A user" : applicant) +
            " requested enrollment" + (programName.isEmpty() ? "" : (" to " + programName)) + ".");
        n.setRecipientUsername(null);
        n.setAudienceRole("ADMIN");         // admin-only
      }
      case "enrollment.status.changed" -> {
        String citizen = safeStr(payload.get("citizenUsername"));
        String status = safeStr(payload.get("status")).toUpperCase();
        boolean approved = "APPROVED".equals(status);
        n.setTitle(approved ? "Your enrollment was approved" : "Your enrollment was rejected");
        String note = safeStr(payload.get("note"));
        n.setBody(approved ? "Congratulations! Your enrollment was approved."
                           : ("Your enrollment was rejected." + (note.isEmpty() ? "" : (" Reason: " + note))));
        n.setRecipientUsername(citizen);
        n.setAudienceRole(null);
      }
      case "payment.succeeded" -> {
        String payee = safeStr(payload.get("payeeUsername"));
        String amount = String.valueOf(payload.getOrDefault("amount", ""));
        String currency = safeStr(payload.get("currency"));
        n.setTitle("Payment received");
        n.setBody("You received a payment" +
            (amount.isBlank() ? "" : (" of " + amount + (currency.isBlank() ? "" : " " + currency))) + ".");
        n.setRecipientUsername(payee);
        n.setAudienceRole(null);
      }
      case "payment.batch.completed" -> {
        n.setTitle("Payment batch completed");
        n.setBody("A payment batch has completed. Open the Payments dashboard for details.");
        n.setRecipientUsername(null);
        n.setAudienceRole("ADMIN");
      }
      default -> {
        // Generic fallback
        n.setTitle(type);
        n.setBody("You have a new notification.");
        n.setRecipientUsername(null);
        n.setAudienceRole(null);
      }
    }

    repo.save(n);
    return ResponseEntity.accepted().build();
  }

  private static String safeStr(Object o) { return o == null ? "" : String.valueOf(o).trim(); }

  // ========== User feed ==========
  @GetMapping("/me")
  public ResponseEntity<?> my(@RequestHeader HttpHeaders headers,
                              @RequestParam(defaultValue = "0") @Min(0) int page,
                              @RequestParam(defaultValue = "20") @Min(1) int size) {
    String user = HeaderAuth.username(headers);
    if (user == null || user.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "missing X-Auth-User"));
    }
    boolean isAdmin = HeaderAuth.isAdmin(headers);
    Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
    Page<Notification> feed = repo.feedForUser(user, isAdmin, pageable);
    return ResponseEntity.ok(feed);
  }

  @GetMapping("/me/count")
  public ResponseEntity<?> myCount(@RequestHeader HttpHeaders headers) {
    String user = HeaderAuth.username(headers);
    if (user == null || user.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "missing X-Auth-User"));
    }
    boolean isAdmin = HeaderAuth.isAdmin(headers);
    long count = repo.countNewForUser(user, isAdmin, NotificationStatus.NEW);
    return ResponseEntity.ok(Map.of("count", count));
  }
}
