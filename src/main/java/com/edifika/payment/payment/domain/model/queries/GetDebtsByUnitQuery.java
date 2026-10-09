package com.edifika.payment.payment.domain.model.queries;

/**
 * Query para obtener las deudas de una unidad residencial (US21).
 */
public record GetDebtsByUnitQuery(Long unitId) {
    public GetDebtsByUnitQuery {
        if (unitId == null) throw new IllegalArgumentException("La unidad es obligatoria");
    }
}
