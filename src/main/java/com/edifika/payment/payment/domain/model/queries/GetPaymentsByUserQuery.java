package com.edifika.payment.payment.domain.model.queries;

/**
 * Query para obtener el historial completo de pagos de un residente (US28).
 */
public record GetPaymentsByUserQuery(Long userId) {
    public GetPaymentsByUserQuery {
        if (userId == null) throw new IllegalArgumentException("El usuario es obligatorio");
    }
}
