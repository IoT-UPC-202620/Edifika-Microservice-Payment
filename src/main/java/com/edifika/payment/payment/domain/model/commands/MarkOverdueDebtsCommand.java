package com.edifika.payment.payment.domain.model.commands;

import java.time.LocalDate;

/**
 * Comando para marcar como vencidas (OVERDUE) las deudas pendientes cuya fecha
 * de vencimiento es anterior a {@code referenceDate}. Lo dispara el proceso programado diario.
 */
public record MarkOverdueDebtsCommand(LocalDate referenceDate) {
    public MarkOverdueDebtsCommand {
        if (referenceDate == null) throw new IllegalArgumentException("La fecha de referencia es obligatoria");
    }
}
