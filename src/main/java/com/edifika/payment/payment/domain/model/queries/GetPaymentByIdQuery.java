package com.edifika.payment.payment.domain.model.queries;

/**
 * Query para obtener un pago por su identificador.
 */
public record GetPaymentByIdQuery(Long paymentId) {
    public GetPaymentByIdQuery {
        if (paymentId == null) throw new IllegalArgumentException("El pago es obligatorio");
    }
}
