package com.edifika.payment.payment.domain.model.valueobjects;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Value object que representa el método con el que un residente paga una deuda.
 * Determina si el cobro se procesa automáticamente contra la pasarela externa (Culqi)
 * o si queda registrado a la espera de revisión manual del administrador (US22/US23).
 */
public enum PaymentMethod {
    CULQI(true),
    VOUCHER(false),
    TRANSFER(false),
    MANUAL(false);

    private final boolean processedByGateway;

    PaymentMethod(boolean processedByGateway) {
        this.processedByGateway = processedByGateway;
    }

    /**
     * @return true si el cobro se realiza en línea contra la pasarela de pagos.
     */
    public boolean requiresGateway() {
        return processedByGateway;
    }

    /**
     * Convierte un texto (sin distinguir mayúsculas) en un método de pago válido.
     *
     * @throws IllegalArgumentException si el valor es vacío o no está soportado.
     */
    public static PaymentMethod fromValue(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("El método de pago es obligatorio");
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Método de pago no soportado: " + value + ". Valores permitidos: " + Arrays.toString(values()));
        }
    }

    /**
     * @return nombres de los métodos cuyos pagos requieren validación manual de un administrador.
     */
    public static List<String> manualReviewMethodNames() {
        return Arrays.stream(values())
                .filter(method -> !method.requiresGateway())
                .map(Enum::name)
                .toList();
    }
}
