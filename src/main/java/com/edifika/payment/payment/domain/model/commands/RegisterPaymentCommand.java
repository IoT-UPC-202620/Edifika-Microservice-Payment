package com.edifika.payment.payment.domain.model.commands;

import com.edifika.payment.payment.domain.model.valueobjects.PaymentMethod;
import com.edifika.payment.shared.domain.model.valueobjects.Email;

/**
 * Comando para registrar un intento de pago sobre una deuda existente.
 * <p>
 * No incluye monto ni moneda: el cargo se hace siempre por el monto de la deuda
 * registrada en base de datos, nunca por un valor enviado por el cliente.
 * <p>
 * {@code idempotencyKey} identifica el intento (header Idempotency-Key): si llega repetida
 * se devuelve el pago ya registrado sin volver a cobrar.
 * Si el método se procesa con la pasarela (ej. CULQI) son obligatorios el token de la
 * tarjeta generado en la app ({@code gatewayToken}) y el correo del pagador ({@code payerEmail}).
 * El token no se persiste: solo viaja hasta la pasarela.
 */
public record RegisterPaymentCommand(
        Long debtId,
        Long userId,
        PaymentMethod paymentMethod,
        String idempotencyKey,
        String gatewayToken,
        String payerEmail
) {
    /** Longitud máxima de la clave de idempotencia (coincide con la columna en BD). */
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    public RegisterPaymentCommand {
        if (debtId == null) throw new IllegalArgumentException("La deuda es obligatoria");
        if (userId == null) throw new IllegalArgumentException("El usuario es obligatorio");
        if (paymentMethod == null) throw new IllegalArgumentException("El método de pago es obligatorio");
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new IllegalArgumentException("El header Idempotency-Key es obligatorio");
        idempotencyKey = idempotencyKey.trim();
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw new IllegalArgumentException(
                    "El header Idempotency-Key no puede superar " + MAX_IDEMPOTENCY_KEY_LENGTH + " caracteres");
        if (paymentMethod.requiresGateway()) {
            if (gatewayToken == null || gatewayToken.isBlank())
                throw new IllegalArgumentException("El token de la tarjeta es obligatorio para pagos con " + paymentMethod);
            new Email(payerEmail);
        }
    }

    public boolean requiresGateway() {
        return paymentMethod.requiresGateway();
    }
}
