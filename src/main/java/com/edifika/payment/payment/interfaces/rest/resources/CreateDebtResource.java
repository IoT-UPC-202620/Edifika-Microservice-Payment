package com.edifika.payment.payment.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resource de entrada para registrar una nueva deuda (TS07 - Escenario 1).
 */
public record CreateDebtResource(
        Long unitId,
        String description,
        BigDecimal amount,
        String currency,
        LocalDate dueDate
) {
}
