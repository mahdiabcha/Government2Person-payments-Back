package com.mini.g2p.payment.dto;

public record PaymentStatusMsg(
    Long instructionId,
    String status,   // "SUCCESS" | "FAILED"
    String bankRef,
    String reason    // was failReason
) {}
