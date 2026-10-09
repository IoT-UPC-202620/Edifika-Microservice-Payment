package com.edifika.payment.payment.domain.model.commands;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Comando para registrar una nueva deuda asociada a una unidad residencial (TS07 - Escenario 1).
 */
public record CreateDebtCommand(
        Long unitId,
        String description,
        BigDecimal amount,
        String currency,
        LocalDate dueDate
) {
    public CreateDebtCommand {
        if (unitId == null) throw new IllegalArgumentException("La unidad es obligatoria");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("La descripción de la deuda es obligatoria");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("El monto ingresado no es válido, verifique los datos");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("La moneda es obligatoria");
        if (dueDate == null) throw new IllegalArgumentException("La fecha de vencimiento es obligatoria");
    }
}
