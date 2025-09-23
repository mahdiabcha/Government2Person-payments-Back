package com.mini.g2p.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="payment_batches", indexes = { @Index(name="ix_batch_program", columnList="programId"), @Index(name="ix_batch_cycle", columnList="cycleId") })
public class PaymentBatch {
  public enum Status { PENDING, PROCESSING, COMPLETED, FAILED }

  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  private Long programId;
  private Long cycleId;
  private String createdBy;
  @Column(updatable=false) private Instant createdAt;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status = Status.PENDING;
  private Integer totalCount;
  private Integer successCount;
  private Integer failedCount;

  @PrePersist void pre(){ if(createdAt==null) createdAt=Instant.now(); }

  // getters/setters
  public Long getId(){return id;}
  public Long getProgramId(){return programId;} public void setProgramId(Long v){this.programId=v;}
  public Long getCycleId(){return cycleId;} public void setCycleId(Long v){this.cycleId=v;}
  public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){this.createdBy=v;}
  public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){this.createdAt=v;}
  public Status getStatus(){return status;} public void setStatus(Status v){this.status=v;}
  public Integer getTotalCount(){return totalCount;} public void setTotalCount(Integer v){this.totalCount=v;}
  public Integer getSuccessCount(){return successCount;} public void setSuccessCount(Integer v){this.successCount=v;}
  public Integer getFailedCount(){return failedCount;} public void setFailedCount(Integer v){this.failedCount=v;}
}