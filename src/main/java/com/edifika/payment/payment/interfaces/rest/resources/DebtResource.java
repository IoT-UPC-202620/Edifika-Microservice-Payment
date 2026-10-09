package com.edifika.payment.payment.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resource de salida que representa una deuda.
 */
public record DebtResource(
        Long id,
        Long unitId,
        String description,
        BigDecimal amount,
        String currency,
        LocalDate dueDate,
        String status
) {
}
