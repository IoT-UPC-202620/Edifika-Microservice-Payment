package com.edifika.payment.payment.domain.model.commands;

import com.edifika.payment.payment.domain.model.valueobjects.PaymentMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de las validaciones de {@link RegisterPaymentCommand} y {@link PaymentMethod}.
 */
class RegisterPaymentCommandTest {

    private static final String KEY = "3f6c1b8e-7a2d-4c1e-9b0a-2d5e8f1a6c34";

    @Test
    void shouldRequireIdempotencyKey() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new RegisterPaymentCommand(1L, 1L, PaymentMethod.VOUCHER, " ", null, null));

        assertTrue(ex.getMessage().contains("Idempotency-Key"));
    }

    @Test
    void shouldLimitIdempotencyKeyLength() {
        String tooLong = "k".repeat(RegisterPaymentCommand.MAX_IDEMPOTENCY_KEY_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () ->
                new RegisterPaymentCommand(1L, 1L, PaymentMethod.VOUCHER, tooLong, null, null));
    }

    @Test
    void shouldRequireCardTokenForGatewayPayments() {
        assertThrows(IllegalArgumentException.class, () ->
                new RegisterPaymentCommand(1L, 1L, PaymentMethod.CULQI, KEY, null, "vecino@edifika.com"));
    }

    @Test
    void shouldRequireValidEmailForGatewayPayments() {
        assertThrows(IllegalArgumentException.class, () ->
                new RegisterPaymentCommand(1L, 1L, PaymentMethod.CULQI, KEY, "tkn_test", null));
    }

    @Test
    void shouldNotRequireTokenNorEmailForManualPayments() {
        RegisterPaymentCommand command = new RegisterPaymentCommand(1L, 1L, PaymentMethod.VOUCHER, KEY, null, null);

        assertFalse(command.requiresGateway());
    }

    @Test
    void shouldParsePaymentMethodIgnoringCase() {
        assertEquals(PaymentMethod.CULQI, PaymentMethod.fromValue(" culqi "));
    }

    @Test
    void shouldRejectUnknownOrMissingPaymentMethod() {
        assertThrows(IllegalArgumentException.class, () -> PaymentMethod.fromValue("CULQUI"));
        assertThrows(IllegalArgumentException.class, () -> PaymentMethod.fromValue(null));
    }
}
