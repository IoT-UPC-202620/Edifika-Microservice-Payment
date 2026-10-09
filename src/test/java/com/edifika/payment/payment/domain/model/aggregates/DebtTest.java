package com.edifika.payment.payment.domain.model.aggregates;

import com.edifika.payment.payment.domain.exceptions.DebtAlreadyPaidException;
import com.edifika.payment.payment.domain.model.commands.CreateDebtCommand;
import com.edifika.payment.payment.domain.model.events.DebtOverdueEvent;
import com.edifika.payment.payment.domain.model.events.DebtPaidEvent;
import com.edifika.payment.payment.domain.model.valueobjects.DebtStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de las invariantes del aggregate {@link Debt}.
 */
class DebtTest {

    @Test
    void shouldAllowPayingPendingDebt() {
        Debt debt = buildDebt();

        assertDoesNotThrow(debt::ensureCanBePaid);
    }

    @Test
    void shouldRejectPaymentOfPaidDebt() {
        Debt debt = buildDebt();
        debt.markAsPaid();

        assertThrows(DebtAlreadyPaidException.class, debt::ensureCanBePaid);
    }

    @Test
    void shouldRegisterDebtPaidEventOnlyOnce() {
        Debt debt = buildDebt();

        debt.markAsPaid();
        debt.markAsPaid();

        assertEquals(DebtStatus.PAID, debt.getStatus());
        assertEquals(1, domainEvents(debt).stream().filter(DebtPaidEvent.class::isInstance).count());
    }

    @Test
    void shouldMarkAsOverdueAndRegisterEvent() {
        Debt debt = buildDebt();

        debt.markAsOverdue();

        assertEquals(DebtStatus.OVERDUE, debt.getStatus());
        assertTrue(domainEvents(debt).stream().anyMatch(DebtOverdueEvent.class::isInstance));
    }

    @Test
    void shouldNotMarkPaidDebtAsOverdue() {
        Debt debt = buildDebt();
        debt.markAsPaid();

        assertThrows(IllegalStateException.class, debt::markAsOverdue);
    }

    @Test
    void shouldAllowPayingAnOverdueDebt() {
        Debt debt = buildDebt();
        debt.markAsOverdue();

        assertDoesNotThrow(debt::ensureCanBePaid);
        debt.markAsPaid();

        assertEquals(DebtStatus.PAID, debt.getStatus());
    }

    private Debt buildDebt() {
        return new Debt(new CreateDebtCommand(1L, "Mantenimiento", new BigDecimal("250.00"), "PEN", LocalDate.now()));
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> domainEvents(Debt debt) {
        return (Collection<Object>) ReflectionTestUtils.invokeMethod(debt, "domainEvents");
    }
}
