package com.edifika.payment.payment.domain.exceptions;

/**
 * Excepción de dominio lanzada cuando se intenta operar sobre un pago inexistente.
 */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(Long paymentId) {
        super("No se encontró el pago con id: " + paymentId);
    }
}
