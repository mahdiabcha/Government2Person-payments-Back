package com.mini.g2p.payment.repo;

import com.mini.g2p.payment.domain.PaymentBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, Long> {}