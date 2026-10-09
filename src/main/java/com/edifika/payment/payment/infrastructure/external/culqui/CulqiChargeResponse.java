package com.edifika.payment.payment.infrastructure.external.culqui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de la respuesta exitosa (HTTP 2xx) de Culqi al crear un cargo.
 * Mapea solo los campos relevantes: el id del cargo, su resultado ({@code outcome})
 * y, de la fuente ({@code source}), los últimos 4 dígitos y la marca de la tarjeta.
 * El número de tarjeta enmascarado que también envía Culqi se ignora a propósito.
 * Si Culqi devuelve {@code action_code = "REVIEW"}, el banco exige autenticación 3D Secure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CulqiChargeResponse(
        String id,
        Outcome outcome,
        Source source,
        @JsonProperty("action_code") String actionCode
) {
    static final String SUCCESSFUL_SALE = "venta_exitosa";
    static final String REVIEW_REQUIRED = "REVIEW";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Outcome(
            String type,
            String code,
            @JsonProperty("merchant_message") String merchantMessage,
            @JsonProperty("user_message") String userMessage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(
            @JsonProperty("last_four") String lastFour,
            Iin iin
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Iin(
            @JsonProperty("card_brand") String cardBrand
    ) {}

    public boolean isApproved() {
        return outcome != null && SUCCESSFUL_SALE.equalsIgnoreCase(outcome.type());
    }

    public boolean requiresAuthentication() {
        return REVIEW_REQUIRED.equalsIgnoreCase(actionCode);
    }

    public String merchantMessage() {
        return outcome != null ? outcome.merchantMessage() : null;
    }

    public String cardLastFour() {
        return source != null ? source.lastFour() : null;
    }

    public String cardBrand() {
        return (source != null && source.iin() != null) ? source.iin().cardBrand() : null;
    }
}
