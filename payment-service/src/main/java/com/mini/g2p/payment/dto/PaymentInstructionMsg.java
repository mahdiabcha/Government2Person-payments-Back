
package com.mini.g2p.payment.dto;

public record PaymentInstructionMsg(
    Long instructionId,
    Long programId,
    String beneficiary,
    Double amount,
    String currency
) {}
