package com.edifika.payment.payment.domain.model.aggregates;

import com.edifika.payment.payment.domain.exceptions.DebtAlreadyPaidException;
import com.edifika.payment.payment.domain.model.commands.CreateDebtCommand;
import com.edifika.payment.payment.domain.model.events.DebtOverdueEvent;
import com.edifika.payment.payment.domain.model.events.DebtPaidEvent;
import com.edifika.payment.payment.domain.model.valueobjects.DebtStatus;
import com.edifika.payment.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.edifika.payment.shared.domain.model.valueobjects.Money;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Aggregate root que representa una deuda asociada a una unidad residencial.
 * <p>
 * Ciclo de vida: PENDING → OVERDUE (por vencimiento) → PAID (por un pago confirmado).
 * Protege sus invariantes: una deuda pagada no se vuelve a pagar ni a vencer.
 * Su monto es la única fuente del importe a cobrar.
 */
@Getter
@Entity
public class Debt extends AuditableAbstractAggregateRoot<Debt> {

    @Column(nullable = false)
    private Long unitId;

    @Column(nullable = false)
    private String description;

    @Embedded
    private Money amount;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebtStatus status;

    protected Debt() {}

    public Debt(CreateDebtCommand command) {
        this.unitId = command.unitId();
        this.description = command.description().trim();
        this.amount = new Money(command.amount(), command.currency());
        this.dueDate = command.dueDate();
        this.status = DebtStatus.PENDING;
    }

    /**
     * Verifica que la deuda todavía pueda pagarse.
     *
     * @throws DebtAlreadyPaidException si la deuda ya fue pagada.
     */
    public void ensureCanBePaid() {
        if (isPaid()) {
            throw new DebtAlreadyPaidException(getId());
        }
    }

    /**
     * Marca la deuda como pagada. Invocado por el PaymentSaga
     * al confirmar exitosamente un Payment asociado. Es idempotente.
     */
    public void markAsPaid() {
        if (isPaid()) {
            return;
        }
        this.status = DebtStatus.PAID;
        this.addDomainEvent(new DebtPaidEvent(this.getId(), this.unitId));
    }

    /**
     * Marca la deuda como vencida. Es idempotente para deudas ya vencidas.
     *
     * @throws IllegalStateException si la deuda ya está pagada.
     */
    public void markAsOverdue() {
        if (isPaid()) {
            throw new IllegalStateException("No se puede marcar como vencida una deuda ya pagada");
        }
        if (this.status == DebtStatus.OVERDUE) {
            return;
        }
        this.status = DebtStatus.OVERDUE;
        this.addDomainEvent(new DebtOverdueEvent(this.getId(), this.unitId, this.dueDate));
    }

    public boolean isPaid() {
        return this.status == DebtStatus.PAID;
    }
}
