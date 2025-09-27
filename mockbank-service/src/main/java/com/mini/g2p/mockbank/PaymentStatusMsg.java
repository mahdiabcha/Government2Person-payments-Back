package com.mini.g2p.mockbank;

public record PaymentStatusMsg(
    Long instructionId,
    String status,
    String bankRef,
    String reason    // align with Payments
) {}
