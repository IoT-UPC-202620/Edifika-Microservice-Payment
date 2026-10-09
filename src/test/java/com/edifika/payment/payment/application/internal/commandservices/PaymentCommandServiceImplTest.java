package com.edifika.payment.payment.application.internal.commandservices;

import com.edifika.payment.payment.application.internal.outboundservices.acl.ExternalUnitService;
import com.edifika.payment.payment.application.internal.sagas.PaymentSaga;
import com.edifika.payment.payment.domain.exceptions.UnitNotFoundException;
import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.commands.ConfirmPaymentCommand;
import com.edifika.payment.payment.domain.model.commands.CreateDebtCommand;
import com.edifika.payment.payment.domain.model.commands.MarkOverdueDebtsCommand;
import com.edifika.payment.payment.domain.model.commands.RegisterPaymentCommand;
import com.edifika.payment.payment.domain.model.valueobjects.DebtStatus;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentMethod;
import com.edifika.payment.payment.domain.services.PaymentCommandService.RegisterPaymentResult;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.DebtRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link PaymentCommandServiceImpl}: creación de deudas con
 * validación de unidad, delegación del flujo de pago al saga y vencimiento de deudas.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceImplTest {

    @Mock
    private DebtRepository debtRepository;

    @Mock
    private PaymentSaga paymentSaga;

    @Mock
    private ExternalUnitService externalUnitService;

    @InjectMocks
    private PaymentCommandServiceImpl paymentCommandService;

    @Test
    void shouldCreateDebtWhenUnitExists() {
        CreateDebtCommand command = buildCreateDebtCommand(LocalDate.now().plusDays(10));
        when(externalUnitService.existsUnitById(1L)).thenReturn(true);
        when(debtRepository.save(any(Debt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Debt> result = paymentCommandService.createDebt(command);

        assertTrue(result.isPresent());
        assertEquals(DebtStatus.PENDING, result.get().getStatus());
        verify(debtRepository).save(any(Debt.class));
        verifyNoInteractions(paymentSaga);
    }

    @Test
    void shouldRejectDebtWhenUnitDoesNotExist() {
        CreateDebtCommand command = buildCreateDebtCommand(LocalDate.now().plusDays(10));
        when(externalUnitService.existsUnitById(1L)).thenReturn(false);

        assertThrows(UnitNotFoundException.class, () -> paymentCommandService.createDebt(command));
        verifyNoInteractions(debtRepository);
    }

    @Test
    void shouldDelegatePaymentRegistrationToSaga() {
        RegisterPaymentCommand command = new RegisterPaymentCommand(
                1L, 1L, PaymentMethod.CULQI, "key-123", "tkn_test_123", "vecino@edifika.com");
        RegisterPaymentResult sagaResult = RegisterPaymentResult.created(mock(Payment.class));
        when(paymentSaga.execute(command)).thenReturn(sagaResult);

        Optional<RegisterPaymentResult> result = paymentCommandService.registerPayment(command);

        assertTrue(result.isPresent());
        assertSame(sagaResult, result.get());
        verifyNoInteractions(debtRepository);
    }

    @Test
    void shouldConfirmPaymentManually() {
        ConfirmPaymentCommand command = new ConfirmPaymentCommand(1L, true, "VOUCHER", "op-123", "Voucher validado");
        Payment confirmedPayment = mock(Payment.class);
        when(paymentSaga.confirmManually(command)).thenReturn(confirmedPayment);

        Optional<Payment> result = paymentCommandService.confirmPayment(command);

        assertTrue(result.isPresent());
        assertSame(confirmedPayment, result.get());
        verifyNoInteractions(debtRepository);
    }

    @Test
    void shouldMarkPendingDebtsAsOverdue() {
        LocalDate today = LocalDate.now();
        Debt overdueDebt = new Debt(buildCreateDebtCommand(today.minusDays(1)));
        when(debtRepository.findByStatusAndDueDateBefore(DebtStatus.PENDING, today)).thenReturn(List.of(overdueDebt));

        int marked = paymentCommandService.markOverdueDebts(new MarkOverdueDebtsCommand(today));

        assertEquals(1, marked);
        assertEquals(DebtStatus.OVERDUE, overdueDebt.getStatus());
        verify(debtRepository).saveAll(List.of(overdueDebt));
    }

    private CreateDebtCommand buildCreateDebtCommand(LocalDate dueDate) {
        return new CreateDebtCommand(1L, "Mantenimiento enero", BigDecimal.valueOf(250.00), "PEN", dueDate);
    }
}
