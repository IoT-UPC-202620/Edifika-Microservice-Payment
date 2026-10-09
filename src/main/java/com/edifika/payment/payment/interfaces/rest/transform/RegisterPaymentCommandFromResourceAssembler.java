package com.edifika.payment.payment.interfaces.rest.transform;

import com.edifika.payment.payment.domain.model.commands.RegisterPaymentCommand;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentMethod;
import com.edifika.payment.payment.interfaces.rest.resources.RegisterPaymentResource;

/**
 * Convierte un {@link RegisterPaymentResource} y el header Idempotency-Key en un {@link RegisterPaymentCommand}.
 * La identidad del pagador (id y correo) proviene del usuario autenticado; el correo
 * del body solo se usa como respaldo si el JWT no lo incluye.
 */
public class RegisterPaymentCommandFromResourceAssembler {

    private RegisterPaymentCommandFromResourceAssembler() {
    }

    public static RegisterPaymentCommand toCommandFromResource(RegisterPaymentResource resource,
                                                               String idempotencyKey,
                                                               Long authenticatedUserId,
                                                               String authenticatedUserEmail) {
        String payerEmail = (authenticatedUserEmail != null && !authenticatedUserEmail.isBlank())
                ? authenticatedUserEmail
                : resource.email();

        return new RegisterPaymentCommand(
                resource.debtId(),
                authenticatedUserId,
                PaymentMethod.fromValue(resource.paymentMethod()),
                idempotencyKey,
                resource.culqiToken(),
                payerEmail
        );
    }
}
