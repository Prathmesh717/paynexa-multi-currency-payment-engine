package io.github.prathmesh717.paynexa.dto;

import io.github.prathmesh717.paynexa.domain.GatewayStatus;

public record GatewayResponse(
        String externalTransactionId,
        GatewayStatus status,
        String gatewayReference
) {
}
