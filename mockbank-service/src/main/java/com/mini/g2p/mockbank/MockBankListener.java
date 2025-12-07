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

  public record PaymentInstructionMsg(
      Long instructionId,
      Long programId,
      String username,
      BigDecimal amount,
      String currency
  ) {}

  @RabbitListener(queues = RabbitConfig.Q_INSTR)
  public void onInstruction(PaymentInstructionMsg in) throws InterruptedException {

    Thread.sleep(ThreadLocalRandom.current().nextInt(3000, 5000));

    boolean ok = ThreadLocalRandom.current().nextDouble() < 0.50;

    var statusMsg = new PaymentStatusMsg(
        in.instructionId(),
        ok ? "SUCCESS" : "FAILED",
        "BANK-" + ThreadLocalRandom.current().nextInt(1_000_000),
        ok ? null : "INSUFFICIENT_FUNDS" 
    );

    amqp.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_STATUS, statusMsg);
  }
}
