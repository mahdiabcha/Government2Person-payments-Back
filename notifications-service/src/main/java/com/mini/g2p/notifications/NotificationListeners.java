package com.mini.g2p.notifications;

import com.mini.g2p.notifications.Notification.Audience;
import com.mini.g2p.notifications.Notification.Status;
import com.mini.g2p.notifications.Notification.Type;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationListeners {
  private final NotificationRepository repo;

  // --- program.activated (broadcast to citizens) ---
  public record ProgramActivatedEvent(String eventId, String occurredAt, Long programId, String programName) {}
  @RabbitListener(queues = RabbitConfig.Q_PROGRAM)
  public void onProgramActivated(ProgramActivatedEvent ev){
    var n = Notification.builder()
        .audience(Audience.CITIZEN)
        .username(null)
        .type(Type.PROGRAM_ACTIVATED)
        .status(Status.NEW)
        .title("New program activated")
        .body("Program \""+safe(ev.programName)+"\" is now ACTIVE.")
        .createdAt(now(ev.occurredAt))
        .build();
    repo.save(n);
  }

  // --- enrollment.* (targeted to user) ---
  public record EnrollmentEvent(String eventId, String occurredAt, Long enrollmentId, Long programId,
                                String username, String status, String note) {}
  @RabbitListener(queues = RabbitConfig.Q_ENROLL)
  public void onEnrollmentEvent(EnrollmentEvent ev){
    String title = switch (safe(ev.status()).toUpperCase()) {
      case "REQUESTED" -> "Enrollment requested";
      case "APPROVED"  -> "Enrollment approved";
      case "REJECTED"  -> "Enrollment rejected";
      default -> "Enrollment update";
    };
    String body = (ev.note()==null || ev.note().isBlank())
        ? "Your enrollment status: " + ev.status()
        : "Your enrollment status: " + ev.status() + " — " + ev.note();

    var n = Notification.builder()
        .audience(Audience.CITIZEN)
        .username(ev.username())
        .type(mapEnrollType(ev.status()))
        .status(Status.NEW)
        .title(title)
        .body(body)
        .createdAt(now(ev.occurredAt))
        .build();
    repo.save(n);
  }

  // --- payment.success/failed (targeted to user) ---
  public record PaymentEvent(String eventId, String occurredAt, Long instructionId, Long batchId, Long programId,
                             String username, Double amount, String currency, String status, String reason, String bankRef) {}
  @RabbitListener(queues = RabbitConfig.Q_PAYMENT)
  public void onPaymentEvent(PaymentEvent ev){
    boolean ok = "SUCCESS".equalsIgnoreCase(safe(ev.status()));
    var n = Notification.builder()
        .audience(Audience.CITIZEN)
        .username(ev.username())
        .type(ok? Type.PAYMENT_SUCCESS : Type.PAYMENT_FAILED)
        .status(Status.NEW)
        .title(ok? "Payment processed" : "Payment failed")
        .body(ok
            ? ("Payment of %s %s processed%s").formatted(
                ev.amount(), safe(ev.currency()), ev.bankRef()!=null? (" (ref "+ev.bankRef()+")"):"")
            : ("Payment failed%s").formatted(ev.reason()!=null? (": "+ev.reason()):""))
        .createdAt(now(ev.occurredAt))
        .build();
    repo.save(n);
  }

  private static String safe(Object s){ return s==null? "" : String.valueOf(s); }
  private static Instant now(String iso){ try { return Instant.parse(iso); } catch(Exception e){ return Instant.now(); } }

  private static Type mapEnrollType(String status){
    if (status==null) return Type.ENROLLMENT_REQUESTED;
    return switch (status.toUpperCase()) {
      case "REQUESTED" -> Type.ENROLLMENT_REQUESTED;
      case "APPROVED"  -> Type.ENROLLMENT_APPROVED;
      case "REJECTED"  -> Type.ENROLLMENT_REJECTED;
      default -> Type.ENROLLMENT_REQUESTED;
    };
  }
}
