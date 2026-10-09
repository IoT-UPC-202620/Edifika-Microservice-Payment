package com.edifika.payment.payment.domain.model.aggregates;

import com.edifika.payment.payment.domain.model.commands.RegisterPaymentCommand;
import com.edifika.payment.payment.domain.model.entities.PaymentTransaction;
import com.edifika.payment.payment.domain.model.events.PaymentRegisteredEvent;
import com.edifika.payment.payment.domain.model.events.PaymentRejectedEvent;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentStatus;
import com.edifika.payment.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.edifika.payment.shared.domain.model.valueobjects.Money;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.Date;
import java.util.Objects;

/**
 * Aggregate root que representa un intento de pago realizado por un residente
 * sobre una deuda existente. No referencia el aggregate Debt directamente,
 * solo su id (debtId), respetando el límite de consistencia entre aggregates.
 * <p>
 * Ciclo de vida: REGISTERED → CONFIRMED | REJECTED | PENDING_VERIFICATION,
 * y PENDING_VERIFICATION → CONFIRMED | REJECTED (resolución manual de un ADMIN).
 * Cada transición genera una {@link PaymentTransaction} que el saga persiste como registro técnico.
 * <p>
 * {@code idempotencyKey} es única: garantiza que un mismo intento no genere dos cobros.
 */
@Getter
@Entity
public class Payment extends AuditableAbstractAggregateRoot<Payment> {

    @Column(nullable = false)
    private Long debtId;

    @Column(nullable = false)
    private Long userId;

    @Embedded
    private Money amount;

    @Temporal(TemporalType.TIMESTAMP)
    private Date paymentDate;

    @Column(nullable = false)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(unique = true, length = RegisterPaymentCommand.MAX_IDEMPOTENCY_KEY_LENGTH)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    private PaymentDeclineReason declineReason;

    protected Payment() {}

    /**
     * @param debtAmount monto de la deuda en base de datos; es el único importe que se cobra.
     */
    public Payment(RegisterPaymentCommand command, Money debtAmount) {
        this.debtId = command.debtId();
        this.userId = command.userId();
        this.amount = Objects.requireNonNull(debtAmount, "El monto de la deuda es obligatorio");
        this.paymentMethod = command.paymentMethod().name();
        this.idempotencyKey = command.idempotencyKey();
        this.status = PaymentStatus.REGISTERED;
    }

    /**
     * Confirma el pago tras una respuesta exitosa de la pasarela o una validación manual.
     *
     * @return la transacción aprobada, para su persistencia.
     * @throws IllegalStateException si el pago ya fue confirmado o rechazado.
     */
    public PaymentTransaction confirm(String provider, String providerTransactionId, String responseMessage) {
        ensureAwaitingResolution("confirmar");
        this.status = PaymentStatus.CONFIRMED;
        this.paymentDate = new Date();
        this.declineReason = null;

        PaymentTransaction transaction = new PaymentTransaction(
                this.getId(), resolveProvider(provider), providerTransactionId, responseMessage);
        transaction.approve();

        this.addDomainEvent(new PaymentRegisteredEvent(
                this.getId(), this.debtId, this.userId, this.amount.amount(), this.amount.currency()
        ));
        return transaction;
    }

    /**
     * Rechaza el pago (denegación de la pasarela, error técnico claro o rechazo manual).
     * La deuda asociada NO se modifica y queda libre para un nuevo intento (TS07 - Escenario 3).
     *
     * @param responseMessage detalle interno para auditoría; nunca se muestra al residente.
     * @return la transacción fallida, para su persistencia.
     * @throws IllegalStateException si el pago ya fue confirmado o rechazado.
     */
    public PaymentTransaction reject(String provider, String providerTransactionId,
                                     PaymentDeclineReason reason, String responseMessage) {
        ensureAwaitingResolution("rechazar");
        this.status = PaymentStatus.REJECTED;
        this.declineReason = Objects.requireNonNull(reason, "El motivo de rechazo es obligatorio");

        PaymentTransaction transaction = new PaymentTransaction(
                this.getId(), resolveProvider(provider), providerTransactionId, responseMessage);
        transaction.fail(responseMessage);

        this.addDomainEvent(new PaymentRejectedEvent(this.getId(), this.debtId, this.userId, reason.name()));
        return transaction;
    }

    /**
     * Deja el pago a la espera de verificación porque la pasarela no respondió a tiempo
     * y no se sabe si el cargo se realizó. Mientras tanto la deuda queda bloqueada.
     *
     * @return la transacción en estado PENDING, para su persistencia.
     * @throws IllegalStateException si el pago no está en estado REGISTERED.
     */
    public PaymentTransaction markPendingVerification(String provider, String responseMessage) {
        if (this.status != PaymentStatus.REGISTERED) {
            throw new IllegalStateException(
                    "Solo un pago en estado REGISTERED puede quedar pendiente de verificación (estado actual: " + this.status + ")");
        }
        this.status = PaymentStatus.PENDING_VERIFICATION;
        return new PaymentTransaction(this.getId(), resolveProvider(provider), null, responseMessage);
    }

    /**
     * @return true si el comando corresponde al mismo intento de pago (mismo usuario, deuda y método),
     * es decir, si es un reintento legítimo con la misma clave de idempotencia.
     */
    public boolean isSameAttemptAs(RegisterPaymentCommand command) {
        return Objects.equals(this.userId, command.userId())
                && Objects.equals(this.debtId, command.debtId())
                && Objects.equals(this.paymentMethod, command.paymentMethod().name());
    }

    public boolean belongsTo(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    private void ensureAwaitingResolution(String action) {
        if (!this.status.isAwaitingResolution()) {
            throw new IllegalStateException(
                    "No se puede " + action + " un pago en estado " + this.status);
        }
    }

    private String resolveProvider(String provider) {
        return (provider == null || provider.isBlank()) ? this.paymentMethod : provider.trim();
    }
}
