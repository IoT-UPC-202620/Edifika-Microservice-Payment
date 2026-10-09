package com.edifika.payment.payment.infrastructure.external.culqui;

import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;
import java.util.Map;

/**
 * DTO de la respuesta de error (HTTP 4xx) de Culqi, por ejemplo una tarjeta
 * rechazada (HTTP 402, {@code type = "card_error"}) o una solicitud inválida.
 * <p>
 * Traduce el {@code decline_code} de Culqi al motivo de rechazo del dominio
 * ({@link #toDeclineReason()}). Los errores que no son de tarjeta (llave inválida,
 * solicitud mal formada) se tratan como {@link PaymentDeclineReason#GATEWAY_ERROR}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CulqiErrorResponse(
        String type,
        @JsonProperty("charge_id") String chargeId,
        String code,
        @JsonProperty("decline_code") String declineCode,
        @JsonProperty("merchant_message") String merchantMessage,
        @JsonProperty("user_message") String userMessage
) {
    static final String CARD_ERROR_TYPE = "card_error";

    private static final Map<String, PaymentDeclineReason> DECLINE_REASONS = Map.of(
            "insufficient_funds", PaymentDeclineReason.INSUFFICIENT_FUNDS,
            "incorrect_cvv", PaymentDeclineReason.INCORRECT_CVV,
            "invalid_cvv", PaymentDeclineReason.INCORRECT_CVV,
            "contact_issuer", PaymentDeclineReason.CONTACT_ISSUER,
            "issuer_decline_operation", PaymentDeclineReason.CONTACT_ISSUER,
            "issuer_not_available", PaymentDeclineReason.CONTACT_ISSUER,
            "stolen_card", PaymentDeclineReason.CARD_NOT_ALLOWED,
            "lost_card", PaymentDeclineReason.CARD_NOT_ALLOWED,
            "fraudulent", PaymentDeclineReason.CARD_NOT_ALLOWED
    );

    public boolean isCardError() {
        return CARD_ERROR_TYPE.equalsIgnoreCase(type) || declineCode != null;
    }

    public PaymentDeclineReason toDeclineReason() {
        if (!isCardError()) {
            return PaymentDeclineReason.GATEWAY_ERROR;
        }
        PaymentDeclineReason byDeclineCode = lookup(declineCode);
        if (byDeclineCode != null) return byDeclineCode;
        PaymentDeclineReason byCode = lookup(code);
        return byCode != null ? byCode : PaymentDeclineReason.GENERIC_DECLINE;
    }

    private static PaymentDeclineReason lookup(String culqiCode) {
        return culqiCode == null ? null : DECLINE_REASONS.get(culqiCode.trim().toLowerCase(Locale.ROOT));
    }
}
