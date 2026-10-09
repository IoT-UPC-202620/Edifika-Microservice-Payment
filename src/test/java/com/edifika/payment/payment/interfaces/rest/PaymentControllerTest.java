package com.edifika.payment.payment.interfaces.rest;

import com.edifika.payment.payment.domain.exceptions.PaymentNotFoundException;
import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.commands.ConfirmPaymentCommand;
import com.edifika.payment.payment.domain.model.commands.RegisterPaymentCommand;
import com.edifika.payment.payment.domain.model.queries.*;
import com.edifika.payment.payment.domain.model.valueobjects.DebtStatus;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentMethod;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentStatus;
import com.edifika.payment.payment.domain.services.PaymentCommandService;
import com.edifika.payment.payment.domain.services.PaymentCommandService.RegisterPaymentResult;
import com.edifika.payment.payment.domain.services.PaymentQueryService;
import com.edifika.payment.payment.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.payment.payment.interfaces.rest.resources.*;
import com.edifika.payment.shared.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link PaymentController}: mapeo de recursos, uso de la identidad
 * del JWT, Idempotency-Key (201 nuevo / 200 reintento), motivo de rechazo propio,
 * rechazo manual y reglas de acceso a datos de otros usuarios.
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final AuthenticatedUser RESIDENT =
            new AuthenticatedUser(1L, "vecino", "vecino@edifika.com", Set.of("ROLE_USER"));
    private static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(99L, "admin", "admin@edifika.com", Set.of("ROLE_ADMIN"));
    private static final String IDEMPOTENCY_KEY = "3f6c1b8e-7a2d-4c1e-9b0a-2d5e8f1a6c34";

    @Mock
    private PaymentCommandService paymentCommandService;

    @Mock
    private PaymentQueryService paymentQueryService;

    @InjectMocks
    private PaymentController paymentController;

    @Test
    void shouldCreateDebt() {
        Debt debt1 = buildDebtMock();
        when(paymentCommandService.createDebt(any())).thenReturn(Optional.of(debt1));

        ResponseEntity<DebtResource> response = paymentController.createDebt(buildCreateDebtResource());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        verifyNoInteractions(paymentQueryService);
    }

    @Test
    void shouldGetDebtsByUnit() {
        Debt debt1 = buildDebtMock();
        Debt debt2 = buildDebtMock();
        when(paymentQueryService.getDebtsByUnit(any(GetDebtsByUnitQuery.class)))
                .thenReturn(List.of(debt1, debt2));

        ResponseEntity<List<DebtResource>> response = paymentController.getDebtsByUnit(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        verifyNoInteractions(paymentCommandService);
    }

    @Test
    void shouldRegisterPaymentUsingIdentityFromTokenAndIdempotencyKey() {
        Payment payment = buildPaymentMock(1L);
        when(paymentCommandService.registerPayment(any())).thenReturn(Optional.of(RegisterPaymentResult.created(payment)));

        ResponseEntity<PaymentResource> response =
                paymentController.registerPayment(RESIDENT, IDEMPOTENCY_KEY, buildRegisterPaymentResource());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<RegisterPaymentCommand> captor = ArgumentCaptor.forClass(RegisterPaymentCommand.class);
        verify(paymentCommandService).registerPayment(captor.capture());
        assertEquals(RESIDENT.userId(), captor.getValue().userId());
        assertEquals(RESIDENT.email(), captor.getValue().payerEmail());
        assertEquals(PaymentMethod.CULQI, captor.getValue().paymentMethod());
        assertEquals(IDEMPOTENCY_KEY, captor.getValue().idempotencyKey());
    }

    @Test
    void shouldReturnOkWithExistingPaymentWhenIdempotencyKeyIsRepeated() {
        Payment payment = buildPaymentMock(1L);
        when(paymentCommandService.registerPayment(any())).thenReturn(Optional.of(RegisterPaymentResult.replayed(payment)));

        ResponseEntity<PaymentResource> response =
                paymentController.registerPayment(RESIDENT, IDEMPOTENCY_KEY, buildRegisterPaymentResource());

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldRequireIdempotencyKeyHeader() {
        assertThrows(IllegalArgumentException.class,
                () -> paymentController.registerPayment(RESIDENT, null, buildRegisterPaymentResource()));
        verifyNoInteractions(paymentCommandService);
    }

    @Test
    void shouldExposeOwnDeclineMessageForRejectedPayment() {
        Payment payment = buildPaymentMock(1L);
        when(payment.getStatus()).thenReturn(PaymentStatus.REJECTED);
        when(payment.getDeclineReason()).thenReturn(PaymentDeclineReason.CARD_NOT_ALLOWED);
        when(paymentCommandService.registerPayment(any())).thenReturn(Optional.of(RegisterPaymentResult.created(payment)));

        PaymentResource body = paymentController.registerPayment(RESIDENT, IDEMPOTENCY_KEY, buildRegisterPaymentResource()).getBody();

        assertEquals("REJECTED", body.status());
        assertEquals("CARD_NOT_ALLOWED", body.declineReason());
        assertEquals(PaymentDeclineReason.CARD_NOT_ALLOWED.customerMessage(), body.declineMessage());
    }

    @Test
    void shouldRejectPaymentManually() {
        Payment payment = buildPaymentMock(1L);
        when(paymentCommandService.confirmPayment(any())).thenReturn(Optional.of(payment));

        ResponseEntity<PaymentResource> response =
                paymentController.rejectPayment(1L, new RejectPaymentResource("No se cobró en Culqi"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<ConfirmPaymentCommand> captor = ArgumentCaptor.forClass(ConfirmPaymentCommand.class);
        verify(paymentCommandService).confirmPayment(captor.capture());
        assertFalse(captor.getValue().approved());
        assertEquals(1L, captor.getValue().paymentId());
    }

    @Test
    void shouldConfirmEndpointAlwaysApprove() {
        Payment payment = buildPaymentMock(1L);
        when(paymentCommandService.confirmPayment(any())).thenReturn(Optional.of(payment));

        ResponseEntity<PaymentResource> response = paymentController.confirmPayment(1L,
                new ConfirmPaymentResource("VOUCHER", "op-123", "Voucher validado"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<ConfirmPaymentCommand> captor = ArgumentCaptor.forClass(ConfirmPaymentCommand.class);
        verify(paymentCommandService).confirmPayment(captor.capture());
        assertTrue(captor.getValue().approved());
        assertEquals("op-123", captor.getValue().providerTransactionId());
        verifyNoInteractions(paymentQueryService);
    }

    @Test
    void shouldConfirmPaymentWithoutBody() {
        Payment payment = buildPaymentMock(1L);
        when(paymentCommandService.confirmPayment(any())).thenReturn(Optional.of(payment));

        ResponseEntity<PaymentResource> response = paymentController.confirmPayment(1L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<ConfirmPaymentCommand> captor = ArgumentCaptor.forClass(ConfirmPaymentCommand.class);
        verify(paymentCommandService).confirmPayment(captor.capture());
        assertTrue(captor.getValue().approved());
    }

    @Test
    void shouldGetOwnPayments() {
        Payment payment1 = buildPaymentMock(1L);
        Payment payment2 = buildPaymentMock(1L);
        when(paymentQueryService.getPaymentsByUser(any(GetPaymentsByUserQuery.class)))
                .thenReturn(List.of(payment1, payment2));

        ResponseEntity<List<PaymentResource>> response = paymentController.getPaymentsByUser(RESIDENT, 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldForbidResidentFromReadingPaymentsOfAnotherUser() {
        assertThrows(AccessDeniedException.class, () -> paymentController.getPaymentsByUser(RESIDENT, 2L));
        verifyNoInteractions(paymentQueryService);
    }

    @Test
    void shouldAllowAdminToReadPaymentsOfAnyUser() {
        Payment payment1 = buildPaymentMock(2L);
        when(paymentQueryService.getPaymentHistoryByYear(any(GetPaymentHistoryByYearQuery.class)))
                .thenReturn(List.of(payment1));

        ResponseEntity<List<PaymentResource>> response = paymentController.getPaymentHistoryByYear(ADMIN, 2L, 2026);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldForbidResidentFromReadingPaymentOfAnotherUser() {
        Payment payment1 = buildPaymentMock(2L);
        when(paymentQueryService.getPaymentById(any(GetPaymentByIdQuery.class)))
                .thenReturn(Optional.of(payment1));

        assertThrows(AccessDeniedException.class, () -> paymentController.getPaymentById(RESIDENT, 5L));
    }

    @Test
    void shouldReturnNotFoundWhenPaymentDoesNotExist() {
        when(paymentQueryService.getPaymentById(any(GetPaymentByIdQuery.class))).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> paymentController.getPaymentById(ADMIN, 5L));
    }

    @Test
    void shouldListPendingReviewPayments() {
        Payment payment1 = buildPaymentMock(1L);
        when(paymentQueryService.getPendingReviewPayments(any(GetPendingReviewPaymentsQuery.class)))
                .thenReturn(List.of(payment1));

        ResponseEntity<List<PaymentResource>> response = paymentController.getPendingReviewPayments();

        assertEquals(1, response.getBody().size());
    }

    private CreateDebtResource buildCreateDebtResource() {
        return new CreateDebtResource(1L, "Maintenance debt", BigDecimal.valueOf(250.00), "PEN", LocalDate.now().plusDays(10));
    }

    private RegisterPaymentResource buildRegisterPaymentResource() {
        return new RegisterPaymentResource(1L, "culqi", "tkn_test_123", null);
    }

    private Debt buildDebtMock() {
        Debt debt = mock(Debt.class);
        lenient().when(debt.getId()).thenReturn(1L);
        lenient().when(debt.getUnitId()).thenReturn(1L);
        lenient().when(debt.getAmount()).thenReturn(new Money(BigDecimal.valueOf(250.00), "PEN"));
        lenient().when(debt.getDescription()).thenReturn("Maintenance debt");
        lenient().when(debt.getStatus()).thenReturn(DebtStatus.PENDING);
        return debt;
    }

    private Payment buildPaymentMock(Long ownerUserId) {
        Payment payment = mock(Payment.class);
        lenient().when(payment.getId()).thenReturn(1L);
        lenient().when(payment.getUserId()).thenReturn(ownerUserId);
        lenient().when(payment.getDebtId()).thenReturn(1L);
        lenient().when(payment.getAmount()).thenReturn(new Money(BigDecimal.valueOf(250.00), "PEN"));
        lenient().when(payment.getPaymentDate()).thenReturn(new Date());
        lenient().when(payment.getPaymentMethod()).thenReturn("CULQI");
        lenient().when(payment.getStatus()).thenReturn(PaymentStatus.CONFIRMED);
        return payment;
    }
}
