package com.edifika.payment.payment.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resource de entrada para que un ADMIN rechace manualmente un pago
 * REGISTERED o PENDING_VERIFICATION. Al rechazarlo, la deuda queda libre otra vez.
 */
@Schema(description = "Rechazo manual de un pago")
public record RejectPaymentResource(
        @Schema(description = "Motivo interno del rechazo (auditoría; no se muestra al residente)",
                example = "Culqi confirma que el cargo no se realizó")
        String responseMessage
) {
}
