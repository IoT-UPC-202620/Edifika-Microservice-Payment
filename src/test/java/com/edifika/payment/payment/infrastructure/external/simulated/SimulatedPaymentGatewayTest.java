package com.edifika.payment.payment.infrastructure.external.simulated;

import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.payment.domain.services.PaymentGateway.ChargeRequest;
import com.edifika.payment.payment.domain.services.PaymentGateway.PaymentGatewayResult;
import com.edifika.payment.shared.domain.model.valueobjects.Money;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de {@link SimulatedPaymentGateway}: el resultado depende del número de tarjeta
 * de prueba de Culqi enviado como token {@code sim_<número>}.
 */
class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();

    @Test
    void shouldApproveApprovedTestCardWithLastFourAndBrand() {
        PaymentGatewayResult result = gateway.charge(request("sim_4111111111111111"));

        assertTrue(result.approved());
        assertEquals("1111", result.cardLastFour());
        assertEquals("Visa", result.cardBrand());
        assertNotNull(result.providerTransactionId());
    }

    @ParameterizedTest
    @CsvSource({
            "sim_4000040000000008, INSUFFICIENT_FUNDS",
            "sim_4000020000000000, CARD_NOT_ALLOWED",
            "sim_4000030000000009, CARD_NOT_ALLOWED",
            "sim_5400020000000003, INCORRECT_CVV"
    })
    void shouldDeclineKnownTestCards(String token, PaymentDeclineReason expectedReason) {
        PaymentGatewayResult result = gateway.charge(request(token));

        assertFalse(result.approved());
        assertEquals(expectedReason, result.declineReason());
    }

    @Test
    void shouldApproveTokensThatAreNotKnownTestCards() {
        assertTrue(gateway.charge(request("tkn_test_cualquiera")).approved());
        assertTrue(gateway.charge(request("sim_4999999999999999")).approved());
    }

    @Test
    void shouldIdentifyItselfAsSimulatedProvider() {
        assertEquals("SIMULATED", gateway.providerName());
    }

    private ChargeRequest request(String token) {
        return new ChargeRequest(token, new Money(new BigDecimal("250.00"), "PEN"),
                "vecino@edifika.com", "Pago", 10L, 1L, 5L);
    }
}
