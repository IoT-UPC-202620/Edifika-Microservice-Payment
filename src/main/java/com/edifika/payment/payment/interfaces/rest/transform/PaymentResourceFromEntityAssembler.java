package com.edifika.payment.payment.interfaces.rest.transform;

import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.payment.interfaces.rest.resources.PaymentResource;

/**
 * Convierte el aggregate {@link Payment} en su representación REST {@link PaymentResource}.
 * El mensaje de rechazo es siempre el mensaje propio del dominio, nunca el del proveedor.
 */
public class PaymentResourceFromEntityAssembler {

    private PaymentResourceFromEntityAssembler() {
    }

    public static PaymentResource toResourceFromEntity(Payment entity) {
        PaymentDeclineReason declineReason = entity.getDeclineReason();
        return new PaymentResource(
                entity.getId(),
                entity.getDebtId(),
                entity.getUserId(),
                entity.getAmount().amount(),
                entity.getAmount().currency(),
                entity.getPaymentDate(),
                entity.getPaymentMethod(),
                entity.getStatus().name(),
                declineReason != null ? declineReason.name() : null,
                declineReason != null ? declineReason.customerMessage() : null
        );
    }
}
