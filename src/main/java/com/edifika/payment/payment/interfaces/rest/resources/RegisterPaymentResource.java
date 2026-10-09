package com.edifika.payment.payment.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resource de entrada para registrar un intento de pago.
 * <p>
 * No incluye monto: se cobra siempre el monto de la deuda registrado en el sistema.
 * El usuario que paga se obtiene del JWT, nunca del body.
 * {@code culqiToken} es el token generado por la app al tokenizar la tarjeta con la llave
 * pública de Culqi (obligatorio si {@code paymentMethod = CULQI}). {@code email} solo se usa
 * si el JWT no trae el correo.
 */
@Schema(description = "Solicitud de pago de una deuda")
public record RegisterPaymentResource(
        @Schema(description = "Id de la deuda a pagar", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long debtId,
        @Schema(description = "CULQI, VOUCHER, TRANSFER o MANUAL", example = "CULQI", requiredMode = Schema.RequiredMode.REQUIRED)
        String paymentMethod,
        @Schema(description = "Token de la tarjeta generado por Culqi (obligatorio para CULQI). "
                + "Con payment.gateway.provider=simulated use sim_<número de tarjeta de prueba>",
                example = "tkn_test_xxxxxxxxxxxxxxxx")
        String culqiToken,
        @Schema(description = "Correo del pagador; solo se usa si el JWT no lo incluye", example = "vecino@edifika.com")
        String email
) {
}
