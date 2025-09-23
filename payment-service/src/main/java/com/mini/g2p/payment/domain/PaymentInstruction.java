
package com.mini.g2p.payment.domain;

import jakarta.persistence.*;

@Entity
@Table(name="payment_instructions",
  uniqueConstraints = @UniqueConstraint(name="ux_batch_beneficiary", columnNames={"batchId","beneficiaryUsername"})
)
public class PaymentInstruction {
  public enum Status { PENDING, SENT, SUCCESS, FAILED }

  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  private Long batchId;
  private Long enrollmentId; // optional
  private String beneficiaryUsername;
  private Double amount;
  private String currency;
  @Enumerated(EnumType.STRING) private Status status = Status.PENDING;
  private String bankRef;
  private String failReason;

  // getters/setters
  public Long getId(){return id;}
  public Long getBatchId(){return batchId;} public void setBatchId(Long v){this.batchId=v;}
  public Long getEnrollmentId(){return enrollmentId;} public void setEnrollmentId(Long v){this.enrollmentId=v;}
  public String getBeneficiaryUsername(){return beneficiaryUsername;} public void setBeneficiaryUsername(String v){this.beneficiaryUsername=v;}
  public Double getAmount(){return amount;} public void setAmount(Double v){this.amount=v;}
  public String getCurrency(){return currency;} public void setCurrency(String v){this.currency=v;}
  public Status getStatus(){return status;} public void setStatus(Status v){this.status=v;}
  public String getBankRef(){return bankRef;} public void setBankRef(String v){this.bankRef=v;}
  public String getFailReason(){return failReason;} public void setFailReason(String v){this.failReason=v;}
}
