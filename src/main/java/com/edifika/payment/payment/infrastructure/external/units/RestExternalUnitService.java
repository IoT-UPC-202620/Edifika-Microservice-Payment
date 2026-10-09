package com.edifika.payment.payment.infrastructure.external.units;

import com.edifika.payment.payment.application.internal.outboundservices.acl.ExternalUnitService;
import com.edifika.payment.payment.domain.exceptions.ExternalServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.http.HttpClient;
import java.util.Optional;

/**
 * Adaptador REST de la ACL {@link ExternalUnitService}.
 * <p>
 * Consulta {@code GET {baseUrl}{unitPath}} reenviando el JWT de la solicitud actual:
 * 2xx → la unidad existe, 404 → no existe, cualquier otro caso → servicio no disponible.
 * Si {@code integration.units.validation-enabled=false} no realiza la consulta.
 */
@Slf4j
@Component
public class RestExternalUnitService implements ExternalUnitService {

    private static final String SERVICE_NAME = "Units";

    private final UnitsServiceProperties properties;
    private final RestClient restClient;

    public RestExternalUnitService(UnitsServiceProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean existsUnitById(Long unitId) {
        if (!properties.validationEnabled()) {
            log.debug("Validación de unidades desactivada; se asume que la unidad {} existe", unitId);
            return true;
        }
        try {
            return restClient.get()
                    .uri(properties.unitPath(), unitId)
                    .headers(headers -> currentAuthorizationHeader()
                            .ifPresent(value -> headers.set(HttpHeaders.AUTHORIZATION, value)))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) return true;
                        if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) return false;
                        throw new ExternalServiceUnavailableException(SERVICE_NAME,
                                "respondió con estado HTTP " + response.getStatusCode().value());
                    });
        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException(SERVICE_NAME, ex);
        }
    }

    private static Optional<String> currentAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.ofNullable(attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION));
        }
        return Optional.empty();
    }
}
