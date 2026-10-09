package com.edifika.payment.payment.domain.exceptions;

/**
 * Excepción técnica de la pasarela de pagos. No representa un rechazo de tarjeta:
 * ese caso se modela como un resultado no aprobado con su {@code PaymentDeclineReason}.
 * <p>
 * Distingue dos situaciones:
 * <ul>
 *     <li><b>Fallo claro</b> (5xx, conexión rechazada): el cargo no se realizó; el pago se rechaza con GATEWAY_ERROR.</li>
 *     <li><b>Resultado desconocido</b> ({@link #isOutcomeUnknown()}, ej. timeout esperando la respuesta):
 *     el cargo pudo haberse realizado; el pago queda en PENDING_VERIFICATION.</li>
 * </ul>
 */
public class PaymentGatewayException extends RuntimeException {

    private final boolean outcomeUnknown;

    public PaymentGatewayException(String message) {
        this(message, null, false);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private PaymentGatewayException(String message, Throwable cause, boolean outcomeUnknown) {
        super(message, cause);
        this.outcomeUnknown = outcomeUnknown;
    }

    /**
     * Crea la excepción para el caso en que no se sabe si la pasarela realizó el cargo.
     */
    public static PaymentGatewayException outcomeUnknown(String message, Throwable cause) {
        return new PaymentGatewayException(message, cause, true);
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
