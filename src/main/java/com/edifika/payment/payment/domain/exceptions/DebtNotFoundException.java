package com.edifika.payment.payment.domain.exceptions;

/**
 * Excepción de dominio lanzada cuando se intenta operar sobre una deuda inexistente.
 */
public class DebtNotFoundException extends RuntimeException {
    public DebtNotFoundException(Long debtId) {
        super("No se encontró la deuda con id: " + debtId);
    }
}
