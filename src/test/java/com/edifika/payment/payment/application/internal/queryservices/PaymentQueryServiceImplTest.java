package com.edifika.payment.payment.application.internal.queryservices;

import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.queries.GetDebtsByUnitQuery;
import com.edifika.payment.payment.domain.model.queries.GetPaymentHistoryByYearQuery;
import com.edifika.payment.payment.domain.model.queries.GetPaymentsByUserQuery;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.DebtRepository;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link PaymentQueryServiceImpl}: consultas de deudas y pagos.
 */
@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceImplTest {

    @Mock
    private DebtRepository debtRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentQueryServiceImpl paymentQueryService;

    @Test
    void shouldGetDebtsByUnit() {

        Long unitId = 1L;
        GetDebtsByUnitQuery query = new GetDebtsByUnitQuery(unitId);

        Debt debt1 = mock(Debt.class);
        Debt debt2 = mock(Debt.class);

        List<Debt> expectedDebts = List.of(debt1, debt2);

        when(debtRepository.findByUnitId(unitId))
                .thenReturn(expectedDebts);

        List<Debt> result = paymentQueryService.getDebtsByUnit(query);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertSame(expectedDebts, result);

        verify(debtRepository, times(1))
                .findByUnitId(unitId);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldGetPaymentsByUser() {

        Long userId = 1L;
        GetPaymentsByUserQuery query = new GetPaymentsByUserQuery(userId);

        Payment payment1 = mock(Payment.class);
        Payment payment2 = mock(Payment.class);

        List<Payment> expectedPayments = List.of(payment1, payment2);

        when(paymentRepository.findByUserId(userId))
                .thenReturn(expectedPayments);

        List<Payment> result = paymentQueryService.getPaymentsByUser(query);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertSame(expectedPayments, result);

        verify(paymentRepository, times(1))
                .findByUserId(userId);

        verifyNoInteractions(debtRepository);
    }

    @Test
    void shouldGetPaymentHistoryByYear() {

        Long userId = 1L;
        int year = 2026;

        GetPaymentHistoryByYearQuery query = new GetPaymentHistoryByYearQuery(userId, year);

        Payment payment1 = mock(Payment.class);
        Payment payment2 = mock(Payment.class);

        List<Payment> expectedPayments = List.of(payment1, payment2);

        when(paymentRepository.findByUserIdAndPaymentDateBetween(
                eq(userId),
                any(Date.class),
                any(Date.class)
        )).thenReturn(expectedPayments);

        List<Payment> result = paymentQueryService.getPaymentHistoryByYear(query);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertSame(expectedPayments, result);

        ArgumentCaptor<Date> startDateCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endDateCaptor = ArgumentCaptor.forClass(Date.class);

        verify(paymentRepository, times(1))
                .findByUserIdAndPaymentDateBetween(
                        eq(userId),
                        startDateCaptor.capture(),
                        endDateCaptor.capture()
                );

        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTime(startDateCaptor.getValue());

        assertEquals(year, startCalendar.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, startCalendar.get(Calendar.MONTH));
        assertEquals(1, startCalendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, startCalendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, startCalendar.get(Calendar.MINUTE));
        assertEquals(0, startCalendar.get(Calendar.SECOND));

        Calendar endCalendar = Calendar.getInstance();
        endCalendar.setTime(endDateCaptor.getValue());

        assertEquals(year, endCalendar.get(Calendar.YEAR));
        assertEquals(Calendar.DECEMBER, endCalendar.get(Calendar.MONTH));
        assertEquals(31, endCalendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(23, endCalendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(59, endCalendar.get(Calendar.MINUTE));
        assertEquals(59, endCalendar.get(Calendar.SECOND));

        verifyNoInteractions(debtRepository);
    }
}