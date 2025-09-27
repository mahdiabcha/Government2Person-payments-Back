package com.mini.g2p.payment.amqp;

import com.mini.g2p.payment.domain.PaymentBatch;
import com.mini.g2p.payment.domain.PaymentInstruction;
import com.mini.g2p.payment.dto.PaymentStatusMsg;
import com.mini.g2p.payment.repo.PaymentBatchRepository;
import com.mini.g2p.payment.repo.PaymentInstructionRepository;
import com.mini.g2p.payment.clients.NotificationsClient; // NEW

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
@RequiredArgsConstructor
public class PaymentStatusListener {

  private final PaymentInstructionRepository instrRepo;
  private final PaymentBatchRepository batchRepo;
  private final NotificationsClient notifications; // NEW

@Transactional
@RabbitListener(queues = RabbitConfig.Q_STATUS)
public void onStatus(PaymentStatusMsg msg) {
  var pi = instrRepo.findById(msg.instructionId()).orElse(null);
  if (pi == null) return;

  var prevInstrStatus = pi.getStatus();
  boolean isSuccess = "SUCCESS".equalsIgnoreCase(msg.status());
  var newStatus = isSuccess ? PaymentInstruction.Status.SUCCESS
                            : PaymentInstruction.Status.FAILED;

  // Update instruction (consume reason)
  pi.setStatus(newStatus);
  pi.setBankRef(msg.bankRef());
  pi.setFailReason(isSuccess ? null : msg.reason());
  instrRepo.save(pi);

  // Load batch
  var b = batchRepo.findById(pi.getBatchId()).orElse(null);
  if (b == null) return;

  // Notify on first transition to SUCCESS
  boolean transitionedToSuccess =
      newStatus == PaymentInstruction.Status.SUCCESS &&
      prevInstrStatus != PaymentInstruction.Status.SUCCESS;
  if (transitionedToSuccess) {
    try {
      notifications.paymentSucceeded(
          pi.getId(),
          b.getId(),
          b.getProgramId(),
          b.getCycleId(),
          pi.getBeneficiaryUsername(),
          pi.getAmount(),
          pi.getCurrency(),
          pi.getBankRef()
      );
    } catch (Exception e) {
      System.err.println("paymentSucceeded notify failed for instr " + pi.getId() + ": " + e.getMessage());
    }
  }

  // Recompute counters & maybe complete
  var prevBatchStatus = b.getStatus();
  var items = instrRepo.findByBatchId(b.getId());
  long succ = items.stream().filter(i -> i.getStatus() == PaymentInstruction.Status.SUCCESS).count();
  long fail = items.stream().filter(i -> i.getStatus() == PaymentInstruction.Status.FAILED).count();
  b.setSuccessCount((int) succ);
  b.setFailedCount((int) fail);
  if (b.getTotalCount() != null && b.getTotalCount() == succ + fail) {
    b.setStatus(PaymentBatch.Status.COMPLETED);
  }
  batchRepo.save(b);

  if (prevBatchStatus != PaymentBatch.Status.COMPLETED &&
      b.getStatus() == PaymentBatch.Status.COMPLETED) {
    try {
      notifications.paymentBatchCompleted(
          b.getId(), b.getProgramId(), b.getSuccessCount(), b.getFailedCount(), b.getTotalCount()
      );
    } catch (Exception e) {
      System.err.println("paymentBatchCompleted notify failed for batch " + b.getId() + ": " + e.getMessage());
    }
  }
}
}