package com.edifika.payment.payment.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resource de entrada para que un ADMIN apruebe manualmente un pago REGISTERED o
 * PENDING_VERIFICATION (ej. validación de voucher - US22/US23, o un cargo verificado en Culqi).
 * Para rechazar se usa PUT /{paymentId}/reject.
 */
@Schema(description = "Aprobación manual de un pago")
public record ConfirmPaymentResource(
        @Schema(description = "Proveedor; si se omite se usa el método de pago original", example = "VOUCHER")
        String provider,
        @Schema(description = "Id de la operación en el proveedor o banco", example = "op-123")
        String providerTransactionId,
        @Schema(description = "Detalle interno de la verificación (auditoría)", example = "Voucher validado")
        String responseMessage
) {
}
