package com.edifika.payment.payment.infrastructure.external.culqui;

import com.edifika.payment.payment.domain.exceptions.PaymentGatewayException;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.payment.domain.services.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;

/**
 * Adaptador del puerto {@link PaymentGateway} para la pasarela Culqi (API v2).
 * <p>
 * Crea un cargo con POST {@code /charges} usando el token de tarjeta generado por la app
 * y la llave secreta del comercio. Traduce las respuestas de Culqi:
 * <ul>
 *     <li>2xx con {@code outcome.type = venta_exitosa} → aprobado (con últimos 4 dígitos y marca).</li>
 *     <li>2xx con {@code action_code = REVIEW} (3D Secure) → rechazado GENERIC_DECLINE: el flujo 3DS no está soportado.</li>
 *     <li>4xx de tarjeta (ej. 402) → rechazado con el motivo de dominio según el {@code decline_code}.</li>
 *     <li>4xx que no es de tarjeta (ej. llave inválida) → rechazado GATEWAY_ERROR.</li>
 *     <li>5xx o conexión rechazada → {@link PaymentGatewayException} (fallo claro: el cargo no se hizo).</li>
 *     <li>Timeout esperando la respuesta o respuesta ilegible → {@link PaymentGatewayException#outcomeUnknown}.</li>
 * </ul>
 * Los mensajes de Culqi solo se guardan para auditoría; nunca se muestran al residente.
 * Activo cuando {@code payment.gateway.provider=culqi} (valor por defecto). Si la llave
 * secreta está vacía, la aplicación no arranca.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "culqi", matchIfMissing = true)
public class CulqiPaymentGateway implements PaymentGateway {

    static final String PROVIDER_NAME = "CULQI";
    private static final String CHARGES_PATH = "/charges";
    private static final String AUTHENTICATION_REQUIRED_MESSAGE = "El banco requiere autenticación 3D Secure (no soportada)";

    private final CulqiProperties properties;
    private final RestClient restClient;

    public CulqiPaymentGateway(CulqiProperties properties) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("payment.gateway.provider=culqi requiere la llave secreta de Culqi: "
                    + "defina la variable de entorno CULQI_SECRET_KEY (o use payment.gateway.provider=simulated)");
        }
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.apiUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Override
    public PaymentGatewayResult charge(ChargeRequest request) {
        try {
            return restClient.post()
                    .uri(CHARGES_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(CulqiChargeRequest.from(request))
                    .exchange((httpRequest, response) -> toResult(response.getStatusCode(), response));
        } catch (RestClientException ex) {
            if (isConnectionFailure(ex)) {
                throw new PaymentGatewayException("No se pudo conectar con Culqi", ex);
            }
            throw PaymentGatewayException.outcomeUnknown("Culqi no respondió a tiempo o la respuesta no se pudo leer", ex);
        }
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    private PaymentGatewayResult toResult(HttpStatusCode status,
                                          RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) throws IOException {
        if (status.is2xxSuccessful()) {
            return fromCharge(response.bodyTo(CulqiChargeResponse.class));
        }
        if (status.is4xxClientError()) {
            return fromError(status, response.bodyTo(CulqiErrorResponse.class));
        }
        throw new PaymentGatewayException("Culqi respondió con estado HTTP " + status.value());
    }

    private PaymentGatewayResult fromCharge(CulqiChargeResponse charge) {
        if (charge == null) {
            throw PaymentGatewayException.outcomeUnknown("Culqi devolvió una respuesta exitosa vacía", null);
        }
        if (charge.isApproved()) {
            return PaymentGatewayResult.approved(charge.id(), charge.merchantMessage(),
                    charge.cardLastFour(), charge.cardBrand());
        }
        if (charge.requiresAuthentication()) {
            return PaymentGatewayResult.declined(charge.id(), PaymentDeclineReason.GENERIC_DECLINE, AUTHENTICATION_REQUIRED_MESSAGE);
        }
        return PaymentGatewayResult.declined(charge.id(), PaymentDeclineReason.GENERIC_DECLINE, charge.merchantMessage());
    }

    private PaymentGatewayResult fromError(HttpStatusCode status, CulqiErrorResponse error) {
        if (error == null) {
            log.error("Culqi respondió HTTP {} sin cuerpo", status.value());
            return PaymentGatewayResult.declined(null, PaymentDeclineReason.GATEWAY_ERROR, "HTTP " + status.value());
        }
        PaymentDeclineReason reason = error.toDeclineReason();
        if (reason == PaymentDeclineReason.GATEWAY_ERROR) {
            log.error("Culqi rechazó la solicitud (HTTP {}): type={}, code={}, merchant_message={}",
                    status.value(), error.type(), error.code(), error.merchantMessage());
        } else {
            log.info("Culqi rechazó la tarjeta (HTTP {}): code={}, decline_code={} -> {}",
                    status.value(), error.code(), error.declineCode(), reason);
        }
        return PaymentGatewayResult.declined(error.chargeId(), reason, error.merchantMessage());
    }

    /**
     * @return true si la solicitud no llegó a Culqi (no se pudo conectar), por lo que el cargo no se realizó.
     */
    private static boolean isConnectionFailure(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException
                    || cause instanceof HttpConnectTimeoutException
                    || cause instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    private static JdkClientHttpRequestFactory requestFactory(CulqiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
