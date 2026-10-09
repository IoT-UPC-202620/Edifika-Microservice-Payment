package com.edifika.payment.payment.domain.services;

import com.edifika.payment.payment.domain.exceptions.PaymentGatewayException;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.shared.domain.model.valueobjects.Money;

/**
 * Puerto de dominio que define el contrato para cobrar a través de un proveedor
 * externo de pagos (ej. Culqi). Las implementaciones viven en infrastructure/external
 * y se seleccionan con la propiedad {@code payment.gateway.provider}.
 */
public interface PaymentGateway {

    /**
     * Ejecuta el cobro.
     *
     * @return resultado normalizado; un rechazo de tarjeta se devuelve como {@code approved = false}
     * con su {@link PaymentDeclineReason}.
     * @throws PaymentGatewayException ante un error técnico; si {@code isOutcomeUnknown()} es true,
     *                                 el cargo pudo haberse realizado.
     */
    PaymentGatewayResult charge(ChargeRequest request);

    /**
     * @return nombre del proveedor que se registra en la PaymentTransaction (ej. "CULQI").
     */
    String providerName();

    /**
     * Datos necesarios para crear un cargo, independientes del proveedor.
     *
     * @param sourceToken token de la tarjeta generado en la app.
     * @param amount      monto y moneda a cobrar (tomados de la deuda).
     * @param payerEmail  correo del pagador.
     * @param description descripción visible en el cargo.
     * @param paymentId   id interno del pago (metadato para conciliación).
     * @param debtId      id de la deuda (metadato para conciliación).
     * @param unitId      id de la unidad residencial (metadato para conciliación).
     */
    record ChargeRequest(
            String sourceToken,
            Money amount,
            String payerEmail,
            String description,
            Long paymentId,
            Long debtId,
            Long unitId
    ) {}

    /**
     * Resultado normalizado del cobro, independiente del proveedor usado.
     *
     * @param approved              true si el cargo fue aprobado.
     * @param providerTransactionId id del cargo en el proveedor, si existe.
     * @param declineReason         motivo del rechazo (null si fue aprobado).
     * @param responseMessage       detalle interno para auditoría; nunca se muestra al residente.
     * @param cardLastFour          últimos 4 dígitos de la tarjeta, si el proveedor los informa.
     * @param cardBrand             marca de la tarjeta, si el proveedor la informa.
     */
    record PaymentGatewayResult(
            boolean approved,
            String providerTransactionId,
            PaymentDeclineReason declineReason,
            String responseMessage,
            String cardLastFour,
            String cardBrand
    ) {
        public static PaymentGatewayResult approved(String providerTransactionId, String responseMessage,
                                                    String cardLastFour, String cardBrand) {
            return new PaymentGatewayResult(true, providerTransactionId, null, responseMessage, cardLastFour, cardBrand);
        }

        public static PaymentGatewayResult declined(String providerTransactionId, PaymentDeclineReason reason,
                                                    String responseMessage) {
            return new PaymentGatewayResult(false, providerTransactionId, reason, responseMessage, null, null);
        }
    }
}
