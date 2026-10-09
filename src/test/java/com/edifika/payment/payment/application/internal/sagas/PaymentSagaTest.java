package com.edifika.payment.payment.application.internal.sagas;

import com.edifika.payment.payment.domain.exceptions.DebtAlreadyPaidException;
import com.edifika.payment.payment.domain.exceptions.PaymentAlreadyInProgressException;
import com.edifika.payment.payment.domain.exceptions.PaymentGatewayException;
import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.commands.ConfirmPaymentCommand;
import com.edifika.payment.payment.domain.model.commands.CreateDebtCommand;
import com.edifika.payment.payment.domain.model.commands.RegisterPaymentCommand;
import com.edifika.payment.payment.domain.model.entities.PaymentTransaction;
import com.edifika.payment.payment.domain.model.valueobjects.*;
import com.edifika.payment.payment.domain.services.PaymentCommandService.RegisterPaymentResult;
import com.edifika.payment.payment.domain.services.PaymentGateway;
import com.edifika.payment.payment.domain.services.PaymentGateway.PaymentGatewayResult;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.DebtRepository;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.PaymentTransactionRepository;
import com.edifika.payment.shared.domain.model.valueobjects.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas del flujo del {@link PaymentSaga}: monto tomado de la deuda, idempotencia, cobro aprobado,
 * rechazos con motivo, fallo técnico (compensación), timeout (PENDING_VERIFICATION),
 * resolución manual y persistencia de transacciones.
 */
@ExtendWith(MockitoExtension.class)
class PaymentSagaTest {

    private static final Long DEBT_ID = 1L;
    private static final Long UNIT_ID = 5L;
    private static final Long PAYMENT_ID = 10L;
    private static final Long USER_ID = 7L;
    private static final String KEY = "key-123";
    private static final Money DEBT_AMOUNT = new Money(new BigDecimal("250.00"), "PEN");

    @Mock private DebtRepository debtRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PlatformTransactionManager transactionManager;

    private PaymentSaga paymentSaga;
    private Debt debt;
    private final AtomicReference<Payment> savedPayment = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        paymentSaga = new PaymentSaga(debtRepository, paymentRepository, paymentTransactionRepository,
                paymentGateway, transactionManager);
        debt = new Debt(new CreateDebtCommand(UNIT_ID, "Mantenimiento", DEBT_AMOUNT.amount(), "PEN", LocalDate.now().plusDays(5)));
        ReflectionTestUtils.setField(debt, "id", DEBT_ID);

        lenient().when(debtRepository.findWithLockById(DEBT_ID)).thenReturn(Optional.of(debt));
        lenient().when(debtRepository.findById(DEBT_ID)).thenReturn(Optional.of(debt));
        lenient().when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
            savedPayment.set(payment);
            return payment;
        });
        lenient().when(paymentRepository.findById(PAYMENT_ID)).thenAnswer(invocation -> Optional.ofNullable(savedPayment.get()));
        lenient().when(paymentGateway.providerName()).thenReturn("CULQI");
    }

    @Test
    void shouldChargeDebtAmountAndConfirmWhenGatewayApproves() {
        when(paymentGateway.charge(any())).thenReturn(PaymentGatewayResult.approved("chr_1", "ok", "1111", "Visa"));

        RegisterPaymentResult result = paymentSaga.execute(culqiCommand());

        assertFalse(result.replayed());
        assertEquals(PaymentStatus.CONFIRMED, result.payment().getStatus());
        assertEquals(DEBT_AMOUNT, result.payment().getAmount());
        assertEquals(DebtStatus.PAID, debt.getStatus());
        PaymentTransaction transaction = lastTransaction();
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals("chr_1", transaction.getProviderTransactionId());
        assertEquals("1111", transaction.getCardLastFour());
        assertEquals("Visa", transaction.getCardBrand());
    }

    @Test
    void shouldSendDebtAmountAndMetadataToGateway() {
        when(paymentGateway.charge(any())).thenReturn(PaymentGatewayResult.approved("chr_1", "ok", null, null));

        paymentSaga.execute(culqiCommand());

        ArgumentCaptor<PaymentGateway.ChargeRequest> captor = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(paymentGateway).charge(captor.capture());
        assertEquals(DEBT_AMOUNT, captor.getValue().amount());
        assertEquals(PAYMENT_ID, captor.getValue().paymentId());
        assertEquals(DEBT_ID, captor.getValue().debtId());
        assertEquals(UNIT_ID, captor.getValue().unitId());
        assertEquals("tkn_test_123", captor.getValue().sourceToken());
    }

    @Test
    void shouldRejectWithDeclineReasonAndKeepDebtPending() {
        when(paymentGateway.charge(any())).thenReturn(
                PaymentGatewayResult.declined("chr_2", PaymentDeclineReason.INSUFFICIENT_FUNDS, "interno"));

        RegisterPaymentResult result = paymentSaga.execute(culqiCommand());

        assertEquals(PaymentStatus.REJECTED, result.payment().getStatus());
        assertEquals(PaymentDeclineReason.INSUFFICIENT_FUNDS, result.payment().getDeclineReason());
        assertEquals(DebtStatus.PENDING, debt.getStatus());
        assertEquals(TransactionStatus.FAILED, lastTransaction().getStatus());
    }

    @Test
    void shouldRejectWithGatewayErrorOnClearTechnicalFailure() {
        when(paymentGateway.charge(any())).thenThrow(new PaymentGatewayException("HTTP 503"));

        RegisterPaymentResult result = paymentSaga.execute(culqiCommand());

        assertEquals(PaymentStatus.REJECTED, result.payment().getStatus());
        assertEquals(PaymentDeclineReason.GATEWAY_ERROR, result.payment().getDeclineReason());
        assertEquals(DebtStatus.PENDING, debt.getStatus());
    }

    @Test
    void shouldLeavePaymentPendingVerificationAndFailWhenOutcomeIsUnknown() {
        when(paymentGateway.charge(any())).thenThrow(PaymentGatewayException.outcomeUnknown("timeout", null));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class, () -> paymentSaga.execute(culqiCommand()));

        assertFalse(ex.isOutcomeUnknown());
        assertTrue(ex.getMessage().contains(String.valueOf(PAYMENT_ID)));
        assertEquals(PaymentStatus.PENDING_VERIFICATION, savedPayment.get().getStatus());
        assertEquals(DebtStatus.PENDING, debt.getStatus());
        assertEquals(TransactionStatus.PENDING, lastTransaction().getStatus());
    }

    @Test
    void shouldBlockNewAttemptsWhileDebtHasActivePayment() {
        when(paymentRepository.existsByDebtIdAndStatusIn(eq(DEBT_ID), argThat(statuses ->
                statuses.contains(PaymentStatus.PENDING_VERIFICATION)))).thenReturn(true);

        assertThrows(PaymentAlreadyInProgressException.class, () -> paymentSaga.execute(culqiCommand()));

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldReturnExistingPaymentWithoutChargingWhenIdempotencyKeyIsRepeated() {
        Payment existing = new Payment(culqiCommand(), DEBT_AMOUNT);
        existing.confirm("CULQI", "chr_1", "ok");
        debt.markAsPaid();
        when(paymentRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        RegisterPaymentResult result = paymentSaga.execute(culqiCommand());

        assertTrue(result.replayed());
        assertSame(existing, result.payment());
        verifyNoInteractions(paymentGateway);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldRejectIdempotencyKeyReusedForAnotherPayment() {
        Payment other = new Payment(new RegisterPaymentCommand(99L, USER_ID, PaymentMethod.CULQI, KEY,
                "tkn", "vecino@edifika.com"), DEBT_AMOUNT);
        when(paymentRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(other));

        assertThrows(IllegalStateException.class, () -> paymentSaga.execute(culqiCommand()));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldNotChargePaidDebt() {
        debt.markAsPaid();

        assertThrows(DebtAlreadyPaidException.class, () -> paymentSaga.execute(culqiCommand()));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldLeaveManualPaymentRegisteredWithoutCallingGateway() {
        RegisterPaymentResult result = paymentSaga.execute(
                new RegisterPaymentCommand(DEBT_ID, USER_ID, PaymentMethod.VOUCHER, KEY, null, null));

        assertEquals(PaymentStatus.REGISTERED, result.payment().getStatus());
        verify(paymentGateway, never()).charge(any());
    }

    @Test
    void shouldLetAdminConfirmPendingVerificationPayment() {
        when(paymentGateway.charge(any())).thenThrow(PaymentGatewayException.outcomeUnknown("timeout", null));
        assertThrows(PaymentGatewayException.class, () -> paymentSaga.execute(culqiCommand()));

        Payment result = paymentSaga.confirmManually(new ConfirmPaymentCommand(PAYMENT_ID, true, null, "chr_9", "verificado"));

        assertEquals(PaymentStatus.CONFIRMED, result.getStatus());
        assertEquals(DebtStatus.PAID, debt.getStatus());
    }

    @Test
    void shouldLetAdminRejectPendingVerificationPaymentAndFreeTheDebt() {
        when(paymentGateway.charge(any())).thenThrow(PaymentGatewayException.outcomeUnknown("timeout", null));
        assertThrows(PaymentGatewayException.class, () -> paymentSaga.execute(culqiCommand()));

        Payment result = paymentSaga.confirmManually(new ConfirmPaymentCommand(PAYMENT_ID, false, null, null, "no se cobró"));

        assertEquals(PaymentStatus.REJECTED, result.getStatus());
        assertEquals(PaymentDeclineReason.GENERIC_DECLINE, result.getDeclineReason());
        assertEquals(DebtStatus.PENDING, debt.getStatus());
    }

    @Test
    void shouldConfirmManualPaymentUsingPaymentMethodAsDefaultProvider() {
        paymentSaga.execute(new RegisterPaymentCommand(DEBT_ID, USER_ID, PaymentMethod.VOUCHER, KEY, null, null));

        Payment result = paymentSaga.confirmManually(new ConfirmPaymentCommand(PAYMENT_ID, true, null, "op-123", "Voucher OK"));

        assertEquals(PaymentStatus.CONFIRMED, result.getStatus());
        assertEquals("VOUCHER", lastTransaction().getProvider());
    }

    private RegisterPaymentCommand culqiCommand() {
        return new RegisterPaymentCommand(DEBT_ID, USER_ID, PaymentMethod.CULQI, KEY, "tkn_test_123", "vecino@edifika.com");
    }

    private PaymentTransaction lastTransaction() {
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
