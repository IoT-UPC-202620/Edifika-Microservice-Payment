package com.edifika.payment.payment.domain.exceptions;

/**
 * Excepción de dominio lanzada cuando se intenta pagar una deuda que ya está pagada.
 */
public class DebtAlreadyPaidException extends RuntimeException {
    public DebtAlreadyPaidException(Long debtId) {
        super("La deuda con id " + debtId + " ya se encuentra pagada");
    }
}
