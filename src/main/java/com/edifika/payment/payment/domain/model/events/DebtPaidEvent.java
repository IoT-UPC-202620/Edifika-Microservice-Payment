package com.edifika.payment.payment.domain.model.events;

/**
 * Evento de dominio emitido cuando una deuda cambia su estado a PAGADO.
 * Se publica dentro del microservicio al guardar el aggregate Debt.
 */
public record DebtPaidEvent(Long debtId, Long unitId) {
}