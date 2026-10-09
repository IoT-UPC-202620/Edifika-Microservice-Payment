package com.edifika.payment.payment.domain.model.valueobjects;

/**
 * Value object con el motivo de rechazo de un pago, independiente del proveedor de pagos.
 * Cada motivo tiene un mensaje propio en español para el residente; los mensajes del
 * proveedor (Culqi) nunca se muestran al cliente.
 * <p>
 * {@link #CARD_NOT_ALLOWED} agrupa a propósito tarjeta robada, perdida y fraude,
 * para no revelar a quien usa la tarjeta el motivo exacto del bloqueo.
 */
public enum PaymentDeclineReason {
    INSUFFICIENT_FUNDS("La tarjeta no tiene fondos suficientes."),
    INCORRECT_CVV("El código de seguridad (CVV) de la tarjeta es incorrecto."),
    CONTACT_ISSUER("Su banco no autorizó el pago. Comuníquese con su banco."),
    CARD_NOT_ALLOWED("No es posible procesar el pago con esta tarjeta. Utilice otra tarjeta."),
    GENERIC_DECLINE("El pago fue rechazado. Intente con otra tarjeta o método de pago."),
    GATEWAY_ERROR("No pudimos procesar el pago en este momento. Intente nuevamente más tarde.");

    private final String customerMessage;

    PaymentDeclineReason(String customerMessage) {
        this.customerMessage = customerMessage;
    }

    /**
     * @return mensaje para mostrar al residente.
     */
    public String customerMessage() {
        return customerMessage;
    }
}
