package com.edifika.payment.payment.domain.model.aggregates;

import com.edifika.payment.payment.domain.model.commands.RegisterPaymentCommand;
import com.edifika.payment.payment.domain.model.entities.PaymentTransaction;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentMethod;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentStatus;
import com.edifika.payment.payment.domain.model.valueobjects.TransactionStatus;
import com.edifika.payment.shared.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de las transiciones de estado del aggregate {@link Payment} y de los datos
 * de tarjeta que se permiten guardar en {@link PaymentTransaction}.
 */
class PaymentTest {

    private static final Money DEBT_AMOUNT = new Money(new BigDecimal("250.00"), "PEN");

    @Test
    void shouldTakeAmountFromDebtAndStartRegistered() {
        Payment payment = new Payment(culqiCommand("key-1"), DEBT_AMOUNT);

        assertEquals(DEBT_AMOUNT, payment.getAmount());
        assertEquals(PaymentStatus.REGISTERED, payment.getStatus());
        assertEquals("key-1", payment.getIdempotencyKey());
    }

    @Test
    void shouldRejectWithReasonFromRegistered() {
        Payment payment = new Payment(culqiCommand("key-1"), DEBT_AMOUNT);

        PaymentTransaction transaction = payment.reject("CULQI", "chr_1", PaymentDeclineReason.INSUFFICIENT_FUNDS, "interno");

        assertEquals(PaymentStatus.REJECTED, payment.getStatus());
        assertEquals(PaymentDeclineReason.INSUFFICIENT_FUNDS, payment.getDeclineReason());
        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
    }

    @Test
    void shouldResolvePendingVerificationAsConfirmedOrRejected() {
        Payment confirmed = new Payment(culqiCommand("key-1"), DEBT_AMOUNT);
        confirmed.markPendingVerification("CULQI", "timeout");
        confirmed.confirm(null, "chr_1", "verificado en Culqi");
        assertEquals(PaymentStatus.CONFIRMED, confirmed.getStatus());

        Payment rejected = new Payment(culqiCommand("key-2"), DEBT_AMOUNT);
        rejected.markPendingVerification("CULQI", "timeout");
        rejected.reject(null, null, PaymentDeclineReason.GENERIC_DECLINE, "no se cobró");
        assertEquals(PaymentStatus.REJECTED, rejected.getStatus());
    }

    @Test
    void shouldOnlyMarkRegisteredPaymentsAsPendingVerification() {
        Payment payment = new Payment(culqiCommand("key-1"), DEBT_AMOUNT);
        payment.confirm("CULQI", "chr_1", "ok");

        assertThrows(IllegalStateException.class, () -> payment.markPendingVerification("CULQI", "timeout"));
    }

    @Test
    void shouldNotResolveAPaymentTwice() {
        Payment payment = new Payment(culqiCommand("key-1"), DEBT_AMOUNT);
        payment.confirm("CULQI", "chr_1", "ok");

        assertThrows(IllegalStateException.class,
                () -> payment.reject("CULQI", null, PaymentDeclineReason.GENERIC_DECLINE, "x"));
    }

    @Test
    void shouldRecognizeSameAttemptForIdempotentRetries() {
        Payment payment = new Payment(culqiCommand("key-1"), DEBT_AMOUNT);

        assertTrue(payment.isSameAttemptAs(culqiCommand("key-1")));
        assertFalse(payment.isSameAttemptAs(new RegisterPaymentCommand(
                2L, 7L, PaymentMethod.CULQI, "key-1", "tkn", "vecino@edifika.com")));
    }

    @Test
    void shouldNeverStoreMoreThanLastFourDigitsOfTheCard() {
        PaymentTransaction transaction = new PaymentTransaction(1L, "CULQI", "chr_1", "ok");

        transaction.recordCard("411111******1111", "Visa");
        assertNull(transaction.getCardLastFour());

        transaction.recordCard("1111", "Visa");
        assertEquals("1111", transaction.getCardLastFour());
        assertEquals("Visa", transaction.getCardBrand());
    }

    private RegisterPaymentCommand culqiCommand(String idempotencyKey) {
        return new RegisterPaymentCommand(1L, 7L, PaymentMethod.CULQI, idempotencyKey, "tkn_test", "vecino@edifika.com");
    }
}
