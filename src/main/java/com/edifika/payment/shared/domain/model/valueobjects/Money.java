package com.edifika.payment.shared.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Value object que representa un monto monetario con su moneda (ej. 250.00 PEN).
 * Es inmutable, valida sus invariantes al construirse y normaliza la moneda a mayúsculas.
 * Se usa en deudas y pagos del sistema.
 */
@SuppressWarnings("JpaObjectClassSignatureInspection")
@Embeddable
public record Money(
        @Column(name = "amount", nullable = false)
        BigDecimal amount,
        @Column(name = "currency", nullable = false)
        String currency
) {
    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("El monto no puede ser negativo");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("La moneda es obligatoria");
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}
