package com.mini.g2p.payment.dto;

import java.math.BigDecimal;


public record PaymentInstructionMsg(
    Long instructionId,
    Long programId,
    String username,         
    BigDecimal amount,      
    String currency
) {}
