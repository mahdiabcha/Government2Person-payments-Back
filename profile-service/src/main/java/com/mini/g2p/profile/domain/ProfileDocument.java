package com.mini.g2p.profile.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "profile_documents",
       indexes = @Index(name = "ix_profile_owner", columnList = "owner_username"))
public class ProfileDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_username", nullable = false, length = 100)
  private String ownerUsername;

  /** Logical type (e.g. ID, OTHER) chosen by client */
  @Column(nullable = false, length = 30)
  private String type;

  @Column(name = "content_type", nullable = false, length = 120)
  private String contentType;

  @Column(nullable = false)
  private Long size;

  private String filename;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** Actual file bytes. Force Postgres bytea mapping. */
  @Lob
  @JdbcTypeCode(SqlTypes.BINARY)          // Hibernate 6: forces BINARY/bytea on Postgres
  @Column(name = "data", columnDefinition = "bytea", nullable = false)
  private byte[] data;

  // getters & setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getOwnerUsername() { return ownerUsername; }
  public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public String getContentType() { return contentType; }
  public void setContentType(String contentType) { this.contentType = contentType; }
  public Long getSize() { return size; }
  public void setSize(Long size) { this.size = size; }
  public String getFilename() { return filename; }
  public void setFilename(String filename) { this.filename = filename; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
  public byte[] getData() { return data; }
  public void setData(byte[] data) { this.data = data; }
}
