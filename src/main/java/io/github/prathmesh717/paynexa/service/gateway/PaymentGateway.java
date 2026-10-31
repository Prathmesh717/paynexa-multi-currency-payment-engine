package io.github.prathmesh717.paynexa.service.gateway;

import io.github.prathmesh717.paynexa.dto.GatewayResponse;
import io.github.prathmesh717.paynexa.dto.PaymentProviderRequest;

public interface PaymentGateway {
    GatewayResponse process(PaymentProviderRequest request);
}
