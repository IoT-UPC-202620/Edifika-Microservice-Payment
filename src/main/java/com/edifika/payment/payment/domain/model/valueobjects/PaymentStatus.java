package com.edifika.payment.payment.domain.model.valueobjects;

import java.util.List;

/**
 * Estados del ciclo de vida de un pago.
 * REGISTERED: intento registrado (en proceso con la pasarela o esperando revisión manual).
 * PENDING_VERIFICATION: la pasarela no respondió a tiempo y no se sabe si cobró; un ADMIN lo resuelve.
 * CONFIRMED: cobro aprobado. REJECTED: cobro denegado o fallido.
 */
public enum PaymentStatus {
    REGISTERED,
    PENDING_VERIFICATION,
    CONFIRMED,
    REJECTED;

    /**
     * @return estados que impiden registrar un nuevo pago sobre la misma deuda.
     */
    public static List<PaymentStatus> activeStatuses() {
        return List.of(REGISTERED, PENDING_VERIFICATION, CONFIRMED);
    }

    /**
     * @return true si el pago todavía puede confirmarse o rechazarse.
     */
    public boolean isAwaitingResolution() {
        return this == REGISTERED || this == PENDING_VERIFICATION;
    }
}
