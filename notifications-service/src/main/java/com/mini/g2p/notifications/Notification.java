package com.mini.g2p.notifications;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 4000)
  private String body;

  // null = broadcast
  @Column(nullable = true, length = 128)
  private String recipientUsername;

  // null = anyone; "ADMIN" limits to admins only (MVP)
  @Column(nullable = true, length = 32)
  private String audienceRole;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NotificationStatus status = NotificationStatus.NEW;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  // JSON as TEXT for portability
  @Lob
  @Column(columnDefinition = "TEXT")
  private String metadataJson;

  // Getters & setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }

  public String getRecipientUsername() { return recipientUsername; }
  public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }

  public String getAudienceRole() { return audienceRole; }
  public void setAudienceRole(String audienceRole) { this.audienceRole = audienceRole; }

  public NotificationStatus getStatus() { return status; }
  public void setStatus(NotificationStatus status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public String getMetadataJson() { return metadataJson; }
  public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
