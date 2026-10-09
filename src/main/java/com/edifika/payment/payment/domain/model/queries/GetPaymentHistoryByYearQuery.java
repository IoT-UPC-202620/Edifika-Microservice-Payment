package com.edifika.payment.payment.domain.model.queries;

/**
 * Query para obtener los pagos confirmados de un residente en un año determinado (US28 - E2).
 */
public record GetPaymentHistoryByYearQuery(Long userId, Integer year) {
    public GetPaymentHistoryByYearQuery {
        if (userId == null) throw new IllegalArgumentException("El usuario es obligatorio");
        if (year == null) throw new IllegalArgumentException("El año es obligatorio");
    }
}
