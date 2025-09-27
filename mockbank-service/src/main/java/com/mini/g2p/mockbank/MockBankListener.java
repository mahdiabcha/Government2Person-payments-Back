package com.mini.g2p.mockbank;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MockBankListener {
  private final AmqpTemplate amqp;
  public MockBankListener(AmqpTemplate amqp){ this.amqp = amqp; }

  // Must match payment-service message exactly (BigDecimal amount + username)
  public record PaymentInstructionMsg(
      Long instructionId,
      Long programId,
      String username,
      BigDecimal amount,
      String currency
  ) {}

  @RabbitListener(queues = RabbitConfig.Q_INSTR)
  public void onInstruction(PaymentInstructionMsg in) throws InterruptedException {
    // Simulate processing time
    Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));

    boolean ok = ThreadLocalRandom.current().nextDouble() < 0.85;

    // SUCCESS → bankRef set, reason = null
    // FAILED  → bankRef may still be set, reason populated
    var statusMsg = new PaymentStatusMsg(
        in.instructionId(),
        ok ? "SUCCESS" : "FAILED",
        "BK-" + ThreadLocalRandom.current().nextInt(1_000_000),
        ok ? null : "INSUFFICIENT_FUNDS"  // <-- publish reason on failure
    );

    amqp.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_STATUS, statusMsg);
  }
}
