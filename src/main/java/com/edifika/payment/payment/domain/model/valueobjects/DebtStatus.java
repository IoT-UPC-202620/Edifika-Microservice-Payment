package com.edifika.payment.payment.domain.model.valueobjects;

/**
 * Estados del ciclo de vida de una deuda.
 * PENDING: emitida y por pagar. OVERDUE: venció sin pagarse. PAID: cancelada con un pago confirmado.
 */
public enum DebtStatus {
    PENDING,
    PAID,
    OVERDUE
}
