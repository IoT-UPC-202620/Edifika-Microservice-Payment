package com.edifika.payment.payment.interfaces.rest.transform;

import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.interfaces.rest.resources.DebtResource;

/**
 * Convierte el aggregate {@link Debt} en su representación REST {@link DebtResource}.
 */
public class DebtResourceFromEntityAssembler {

    private DebtResourceFromEntityAssembler() {
    }

    public static DebtResource toResourceFromEntity(Debt entity) {
        return new DebtResource(
                entity.getId(),
                entity.getUnitId(),
                entity.getDescription(),
                entity.getAmount().amount(),
                entity.getAmount().currency(),
                entity.getDueDate(),
                entity.getStatus().name()
        );
    }
}
