package com.edifika.payment.payment.domain.exceptions;

/**
 * Excepción de dominio lanzada cuando una deuda ya tiene un pago registrado
 * (en proceso o en revisión) o confirmado. Evita cobros duplicados.
 */
public class PaymentAlreadyInProgressException extends RuntimeException {
    public PaymentAlreadyInProgressException(Long debtId) {
        super("La deuda con id " + debtId + " ya tiene un pago en proceso o confirmado");
    }
}
