package com.edifika.payment.payment.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Resource de salida que representa un pago.
 * Si el pago fue rechazado incluye el motivo y un mensaje propio en español;
 * nunca el mensaje del proveedor de pagos.
 */
@Schema(description = "Pago de una deuda")
public record PaymentResource(
        @Schema(example = "15") Long id,
        @Schema(example = "1") Long debtId,
        @Schema(example = "7") Long userId,
        @Schema(description = "Monto cobrado; siempre es el monto de la deuda", example = "250.00") BigDecimal amount,
        @Schema(example = "PEN") String currency,
        @Schema(description = "Fecha de confirmación del pago (null si no está confirmado)") Date paymentDate,
        @Schema(example = "CULQI") String paymentMethod,
        @Schema(description = "REGISTERED, PENDING_VERIFICATION, CONFIRMED o REJECTED", example = "CONFIRMED") String status,
        @Schema(description = "Motivo del rechazo (solo si status = REJECTED): INSUFFICIENT_FUNDS, INCORRECT_CVV, "
                + "CONTACT_ISSUER, CARD_NOT_ALLOWED, GENERIC_DECLINE o GATEWAY_ERROR", example = "INSUFFICIENT_FUNDS")
        String declineReason,
        @Schema(description = "Mensaje para mostrar al residente (solo si status = REJECTED)",
                example = "La tarjeta no tiene fondos suficientes.")
        String declineMessage
) {
}
