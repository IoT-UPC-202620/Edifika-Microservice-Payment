package com.edifika.payment.payment.interfaces.rest.transform;

import com.edifika.payment.payment.domain.model.commands.ConfirmPaymentCommand;
import com.edifika.payment.payment.interfaces.rest.resources.ConfirmPaymentResource;
import com.edifika.payment.payment.interfaces.rest.resources.RejectPaymentResource;

/**
 * Convierte los recursos de resolución manual de un pago ({@link ConfirmPaymentResource},
 * {@link RejectPaymentResource}) y el id del pago en un {@link ConfirmPaymentCommand}.
 */
public class ConfirmPaymentCommandFromResourceAssembler {

    private ConfirmPaymentCommandFromResourceAssembler() {
    }

    /**
     * Crea el comando de aprobación (PUT /{paymentId}/confirm).
     *
     * @param resource puede ser null (aprobación sin detalle).
     */
    public static ConfirmPaymentCommand toCommandFromResource(Long paymentId, ConfirmPaymentResource resource) {
        return new ConfirmPaymentCommand(
                paymentId,
                true,
                resource != null ? resource.provider() : null,
                resource != null ? resource.providerTransactionId() : null,
                resource != null ? resource.responseMessage() : null
        );
    }

    /**
     * Crea el comando de rechazo (PUT /{paymentId}/reject).
     *
     * @param resource puede ser null (rechazo sin detalle).
     */
    public static ConfirmPaymentCommand toRejectCommandFromResource(Long paymentId, RejectPaymentResource resource) {
        return new ConfirmPaymentCommand(
                paymentId,
                false,
                null,
                null,
                resource != null ? resource.responseMessage() : null
        );
    }
}
