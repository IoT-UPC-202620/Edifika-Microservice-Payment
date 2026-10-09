package com.edifika.payment.payment.domain.model.events;

/**
 * Evento de dominio emitido cuando un intento de pago es rechazado
 * por la pasarela externa (Culqi) o por validación manual.
 * Se publica dentro del microservicio al guardar el aggregate Payment.
 */
public record PaymentRejectedEvent(
        Long paymentId,
        Long debtId,
        Long userId,
        String reason
) {
}
