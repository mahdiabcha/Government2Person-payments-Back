package com.mini.g2p.notifications;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "notifications",
  indexes = {
    @Index(name="idx_notif_audience_user", columnList = "audience, username"),
    @Index(name="idx_notif_status", columnList = "status"),
    @Index(name="idx_notif_created", columnList = "createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

  public enum Audience { CITIZEN, ADMIN }
  public enum Type {
    PROGRAM_ACTIVATED,
    ENROLLMENT_REQUESTED, ENROLLMENT_APPROVED, ENROLLMENT_REJECTED,
    PAYMENT_SUCCESS, PAYMENT_FAILED
  }
  public enum Status { NEW, READ }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Audience audience;

  @Column(nullable = true, length = 128)
  private String username; // null => broadcast to audience

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Type type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private Status status;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(nullable = false, length = 800)
  private String body;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}
