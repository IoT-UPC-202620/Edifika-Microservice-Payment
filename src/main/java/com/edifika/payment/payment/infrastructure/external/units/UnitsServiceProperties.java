package com.edifika.payment.payment.infrastructure.external.units;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuración de la integración con el microservicio de unidades (prefijo {@code integration.units}).
 *
 * @param validationEnabled si es false no se consulta el servicio y toda unidad se considera válida.
 * @param baseUrl           URL base del microservicio de unidades.
 * @param unitPath          ruta del recurso unidad, con el marcador {@code {unitId}}.
 * @param timeout           tiempo máximo de conexión y de respuesta.
 * @param ownUnitPath       ruta que responde 2xx solo si la unidad es del usuario del JWT (404 si no).
 */
@ConfigurationProperties(prefix = "integration.units")
public record UnitsServiceProperties(
        @DefaultValue("false") boolean validationEnabled,
        @DefaultValue("http://localhost:8082") String baseUrl,
        @DefaultValue("/api/v1/units/{unitId}") String unitPath,
        @DefaultValue("5s") Duration timeout,
        @DefaultValue("/api/v1/residential/units/{unitId}") String ownUnitPath
) {
}
