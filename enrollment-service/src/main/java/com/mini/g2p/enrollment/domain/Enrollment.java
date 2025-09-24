package com.mini.g2p.enrollment.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "enrollments",
       indexes = {
         @Index(name = "idx_enroll_program", columnList = "programId"),
         @Index(name = "idx_enroll_user", columnList = "citizenUsername")
       })
public class Enrollment {

  public enum Status { PENDING, APPROVED, REJECTED }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long programId;
  private String ProgramName; 

  @Column(length = 100, nullable = false)
  private String citizenUsername;


  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private Status status = Status.PENDING;

  @Column(length = 1000)
  private String note;

  @Column(name = "eligibility_passed", nullable = false)
  private boolean eligibilityPassed;

  @Column(name = "eligibility_reason", length = 500)
  private String eligibilityReason;

  @Column(name = "eligibility_checked_at")
  private Instant eligibilityCheckedAt;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @PrePersist
  public void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  public void onUpdate() {
    updatedAt = Instant.now();
  }

  // getters & setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public Long getProgramId() { return programId; }
  public void setProgramId(Long programId) { this.programId = programId; }

  public String getCitizenUsername() { return citizenUsername; }
  public void setCitizenUsername(String citizenUsername) { 
    this.citizenUsername = citizenUsername; 
  }

  public Status getStatus() { return status; }
  public void setStatus(Status status) { this.status = status; }

  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }

  public boolean isEligibilityPassed() { return eligibilityPassed; }
  public void setEligibilityPassed(boolean eligibilityPassed) { this.eligibilityPassed = eligibilityPassed; }

  public String getEligibilityReason() { return eligibilityReason; }
  public void setEligibilityReason(String eligibilityReason) { this.eligibilityReason = eligibilityReason; }

  public Instant getEligibilityCheckedAt() { return eligibilityCheckedAt; }
  public void setEligibilityCheckedAt(Instant eligibilityCheckedAt) { this.eligibilityCheckedAt = eligibilityCheckedAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public Object getProgramName() {
    return ProgramName ; 

  }
}