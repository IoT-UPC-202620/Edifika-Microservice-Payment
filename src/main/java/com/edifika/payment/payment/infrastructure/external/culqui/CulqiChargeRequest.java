package com.edifika.payment.payment.infrastructure.external.culqui;

import com.edifika.payment.payment.domain.services.PaymentGateway.ChargeRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.RoundingMode;
import java.util.Map;

/**
 * DTO del cuerpo de la solicitud hacia la API de Culqi para crear un cargo (POST /v2/charges).
 * Culqi espera el monto en céntimos (ej. S/ 10.00 → 1000) y los campos en snake_case.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CulqiChargeRequest(
        long amount,
        @JsonProperty("currency_code") String currencyCode,
        String email,
        @JsonProperty("source_id") String sourceId,
        String description,
        Map<String, String> metadata
) {
    /** Longitud máxima que Culqi admite en la descripción del cargo. */
    static final int MAX_DESCRIPTION_LENGTH = 80;

    public static CulqiChargeRequest from(ChargeRequest request) {
        return new CulqiChargeRequest(
                toCents(request),
                request.amount().currency(),
                request.payerEmail(),
                request.sourceToken(),
                truncate(request.description()),
                Map.of(
                        "payment_id", String.valueOf(request.paymentId()),
                        "debt_id", String.valueOf(request.debtId()),
                        "unit_id", String.valueOf(request.unitId())
                )
        );
    }

    private static long toCents(ChargeRequest request) {
        return request.amount().amount()
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static String truncate(String description) {
        if (description == null || description.length() <= MAX_DESCRIPTION_LENGTH) return description;
        return description.substring(0, MAX_DESCRIPTION_LENGTH);
    }
}
