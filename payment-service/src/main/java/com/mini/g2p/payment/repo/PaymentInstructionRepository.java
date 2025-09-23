package com.mini.g2p.payment.repo;

import com.mini.g2p.payment.domain.PaymentInstruction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentInstructionRepository extends JpaRepository<PaymentInstruction, Long> {
  List<PaymentInstruction> findByBatchId(Long batchId);
}