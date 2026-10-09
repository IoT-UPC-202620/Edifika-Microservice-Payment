package com.edifika.payment.payment.infrastructure.external.culqui;

import com.edifika.payment.payment.domain.exceptions.PaymentGatewayException;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.payment.domain.services.PaymentGateway.ChargeRequest;
import com.edifika.payment.payment.domain.services.PaymentGateway.PaymentGatewayResult;
import com.edifika.payment.shared.domain.model.valueobjects.Money;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración del adaptador {@link CulqiPaymentGateway} contra WireMock, que imita
 * la API v2 de Culqi: cargo exitoso, rechazos de tarjeta, error de solicitud, 5xx, timeout y 3DS.
 * Verifica también el formato de la solicitud enviada. No hay llamadas reales a Culqi.
 */
class CulqiPaymentGatewayTest {

    private static final String SECRET_KEY = "sk_test_wiremock";
    private static final String CHARGES_URL = "/v2/charges";
    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);

    /**
     * HTTP/2 en texto plano (h2c) desactivado: el HttpClient del JDK intenta el upgrade h2c sobre
     * http:// y WireMock cierra la conexión. Con Culqi real (https) HTTP/2 se negocia por TLS.
     */
    @RegisterExtension
    static WireMockExtension culqi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();

    @Test
    void shouldApproveSuccessfulChargeAndSendExpectedRequest() {
        culqi.stubFor(post(CHARGES_URL).willReturn(jsonResponse(201, """
                {"object":"charge","id":"chr_test_ok","amount":25050,
                 "source":{"object":"token","id":"tkn_test_123","card_number":"411111******1111",
                           "last_four":"1111","iin":{"bin":"411111","card_brand":"Visa"}},
                 "outcome":{"type":"venta_exitosa","code":"AUT0000",
                            "merchant_message":"La operación de venta ha sido autorizada exitosamente",
                            "user_message":"Su compra ha sido exitosa."}}
                """)));

        PaymentGatewayResult result = gateway().charge(chargeRequest());

        assertTrue(result.approved());
        assertEquals("chr_test_ok", result.providerTransactionId());
        assertEquals("1111", result.cardLastFour());
        assertEquals("Visa", result.cardBrand());
        culqi.verify(postRequestedFor(urlEqualTo(CHARGES_URL))
                .withHeader("Authorization", equalTo("Bearer " + SECRET_KEY))
                .withRequestBody(equalToJson("""
                        {"amount":25050,"currency_code":"PEN","email":"vecino@edifika.com",
                         "source_id":"tkn_test_123","description":"Edifika - Pago de deuda #1",
                         "metadata":{"payment_id":"10","debt_id":"1","unit_id":"5"}}
                        """)));
    }

    @Test
    void shouldMapInsufficientFundsDecline() {
        stubDecline("insufficient_funds");

        PaymentGatewayResult result = gateway().charge(chargeRequest());

        assertFalse(result.approved());
        assertEquals(PaymentDeclineReason.INSUFFICIENT_FUNDS, result.declineReason());
        assertEquals("chr_test_declined", result.providerTransactionId());
    }

    @Test
    void shouldGroupStolenLostAndFraudulentCardsAsCardNotAllowed() {
        for (String declineCode : new String[]{"stolen_card", "lost_card", "fraudulent"}) {
            culqi.resetAll();
            stubDecline(declineCode);

            assertEquals(PaymentDeclineReason.CARD_NOT_ALLOWED, gateway().charge(chargeRequest()).declineReason(), declineCode);
        }
    }

    @Test
    void shouldMapIncorrectCvvAndContactIssuer() {
        stubDecline("incorrect_cvv");
        assertEquals(PaymentDeclineReason.INCORRECT_CVV, gateway().charge(chargeRequest()).declineReason());

        culqi.resetAll();
        stubDecline("contact_issuer");
        assertEquals(PaymentDeclineReason.CONTACT_ISSUER, gateway().charge(chargeRequest()).declineReason());
    }

    @Test
    void shouldMapUnknownDeclineCodeToGenericDecline() {
        stubDecline("expired_card");

        assertEquals(PaymentDeclineReason.GENERIC_DECLINE, gateway().charge(chargeRequest()).declineReason());
    }

    @Test
    void shouldTreatNonCardClientErrorAsGatewayError() {
        culqi.stubFor(post(CHARGES_URL).willReturn(jsonResponse(401, """
                {"object":"error","type":"authentication_error",
                 "merchant_message":"La llave secreta es inválida.","user_message":"Error"}
                """)));

        PaymentGatewayResult result = gateway().charge(chargeRequest());

        assertFalse(result.approved());
        assertEquals(PaymentDeclineReason.GATEWAY_ERROR, result.declineReason());
    }

    @Test
    void shouldTreatServerErrorAsClearTechnicalFailure() {
        culqi.stubFor(post(CHARGES_URL).willReturn(aResponse().withStatus(503)));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class, () -> gateway().charge(chargeRequest()));

        assertFalse(ex.isOutcomeUnknown());
    }

    @Test
    void shouldTreatTimeoutAsUnknownOutcome() {
        culqi.stubFor(post(CHARGES_URL).willReturn(jsonResponse(201, "{}")
                .withFixedDelay((int) READ_TIMEOUT.multipliedBy(4).toMillis())));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class, () -> gateway().charge(chargeRequest()));

        assertTrue(ex.isOutcomeUnknown());
    }

    @Test
    void shouldTreatConnectionResetAfterSendingAsUnknownOutcome() {
        culqi.stubFor(post(CHARGES_URL).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class, () -> gateway().charge(chargeRequest()));

        assertTrue(ex.isOutcomeUnknown());
    }

    @Test
    void shouldTreatRefusedConnectionAsClearTechnicalFailure() {
        CulqiPaymentGateway unreachable = new CulqiPaymentGateway(
                new CulqiProperties("http://localhost:1/v2", SECRET_KEY, Duration.ofSeconds(1), READ_TIMEOUT));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class, () -> unreachable.charge(chargeRequest()));

        assertFalse(ex.isOutcomeUnknown());
    }

    @Test
    void shouldRejectChargeThatRequires3DSecure() {
        culqi.stubFor(post(CHARGES_URL).willReturn(jsonResponse(200, """
                {"action_code":"REVIEW","user_message":"Autenticación requerida"}
                """)));

        PaymentGatewayResult result = gateway().charge(chargeRequest());

        assertFalse(result.approved());
        assertEquals(PaymentDeclineReason.GENERIC_DECLINE, result.declineReason());
    }

    @Test
    void shouldFailAtStartupWhenSecretKeyIsMissing() {
        CulqiProperties withoutKey = new CulqiProperties(culqi.baseUrl() + "/v2", "", Duration.ofSeconds(1), READ_TIMEOUT);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new CulqiPaymentGateway(withoutKey));

        assertTrue(ex.getMessage().contains("CULQI_SECRET_KEY"));
    }

    @Test
    void shouldConvertAmountToCentsRoundingHalfUp() {
        ChargeRequest request = new ChargeRequest("tkn", new Money(new BigDecimal("10.005"), "PEN"),
                "vecino@edifika.com", "Pago", 1L, 1L, 1L);

        assertEquals(1001, CulqiChargeRequest.from(request).amount());
    }

    private void stubDecline(String declineCode) {
        culqi.stubFor(post(CHARGES_URL).willReturn(jsonResponse(402, """
                {"object":"error","type":"card_error","charge_id":"chr_test_declined",
                 "code":"card_declined","decline_code":"%s",
                 "merchant_message":"Mensaje interno de Culqi","user_message":"Mensaje de Culqi para el usuario"}
                """.formatted(declineCode))));
    }

    private static ResponseDefinitionBuilder jsonResponse(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }

    private CulqiPaymentGateway gateway() {
        return new CulqiPaymentGateway(
                new CulqiProperties(culqi.baseUrl() + "/v2", SECRET_KEY, Duration.ofSeconds(1), READ_TIMEOUT));
    }

    private ChargeRequest chargeRequest() {
        return new ChargeRequest("tkn_test_123", new Money(new BigDecimal("250.50"), "PEN"),
                "vecino@edifika.com", "Edifika - Pago de deuda #1", 10L, 1L, 5L);
    }
}
