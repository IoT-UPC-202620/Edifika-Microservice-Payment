package com.edifika.payment.payment.domain.model.events;

import java.time.LocalDate;

/**
 * Evento de dominio emitido cuando una deuda pendiente pasa a estado OVERDUE.
 * Se publica dentro del microservicio al guardar el aggregate Debt.
 */
public record DebtOverdueEvent(Long debtId, Long unitId, LocalDate dueDate) {
}
