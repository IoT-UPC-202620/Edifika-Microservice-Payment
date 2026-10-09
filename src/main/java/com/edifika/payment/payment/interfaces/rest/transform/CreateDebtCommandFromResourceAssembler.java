package com.edifika.payment.payment.interfaces.rest.transform;

import com.edifika.payment.payment.domain.model.commands.CreateDebtCommand;
import com.edifika.payment.payment.interfaces.rest.resources.CreateDebtResource;

/**
 * Convierte un {@link CreateDebtResource} en un {@link CreateDebtCommand}.
 */
public class CreateDebtCommandFromResourceAssembler {

    private CreateDebtCommandFromResourceAssembler() {
    }

    public static CreateDebtCommand toCommandFromResource(CreateDebtResource resource) {
        return new CreateDebtCommand(
                resource.unitId(),
                resource.description(),
                resource.amount(),
                resource.currency(),
                resource.dueDate()
        );
    }
}
