package com.edifika.payment.payment.domain.model.queries;

/**
 * Query para obtener una deuda por su identificador.
 */
public record GetDebtByIdQuery(Long debtId) {
    public GetDebtByIdQuery {
        if (debtId == null) throw new IllegalArgumentException("La deuda es obligatoria");
    }
}
