package com.mini.g2p.payment.amqp;

import com.mini.g2p.payment.amqp.RabbitConfig;
import com.mini.g2p.payment.domain.PaymentBatch;
import com.mini.g2p.payment.domain.PaymentInstruction;
import com.mini.g2p.payment.dto.PaymentStatusMsg;
import com.mini.g2p.payment.repo.PaymentBatchRepository;
import com.mini.g2p.payment.repo.PaymentInstructionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentStatusListener {

  private final PaymentInstructionRepository instrRepo;
  private final PaymentBatchRepository batchRepo;

  @Transactional
  @RabbitListener(queues = RabbitConfig.Q_STATUS)  // listens to q.payment.status
  public void onStatus(PaymentStatusMsg msg) {
    // Find instruction
    var pi = instrRepo.findById(msg.instructionId()).orElse(null);
    if (pi == null) return;

    // Update instruction
    var newStatus = "SUCCESS".equalsIgnoreCase(msg.status())
        ? PaymentInstruction.Status.SUCCESS
        : PaymentInstruction.Status.FAILED;
    pi.setStatus(newStatus);
    pi.setBankRef(msg.bankRef());
    pi.setFailReason(msg.reason());
    instrRepo.save(pi);

    // Update batch counters & complete if done
    var b = batchRepo.findById(pi.getBatchId()).orElse(null);
    if (b == null) return;

    List<PaymentInstruction> items = instrRepo.findByBatchId(b.getId());
    long succ = items.stream().filter(i -> i.getStatus()==PaymentInstruction.Status.SUCCESS).count();
    long fail = items.stream().filter(i -> i.getStatus()==PaymentInstruction.Status.FAILED).count();
    b.setSuccessCount((int) succ);
    b.setFailedCount((int) fail);
    if (b.getTotalCount()!=null && b.getTotalCount()==succ+fail) {
      b.setStatus(PaymentBatch.Status.COMPLETED);
    }
    batchRepo.save(b);
  }
}