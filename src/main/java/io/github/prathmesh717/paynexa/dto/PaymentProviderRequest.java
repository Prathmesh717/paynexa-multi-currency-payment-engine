package io.github.prathmesh717.paynexa.dto;

import java.math.BigDecimal;

public record PaymentProviderRequest(
        String externalTransactionId,
        BigDecimal amount,
        String currency,
        String destinationAccount,
        String description
) {
}
