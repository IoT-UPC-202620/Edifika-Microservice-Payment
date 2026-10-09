package com.edifika.payment.payment.infrastructure.external.culqui;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuración de la integración con Culqi (prefijo {@code culqi}).
 *
 * @param apiUrl         URL base de la API (ej. https://api.culqi.com/v2).
 * @param secretKey      llave secreta del comercio (sk_test_... / sk_live_...). Nunca debe exponerse al frontend.
 * @param connectTimeout tiempo máximo para establecer la conexión.
 * @param readTimeout    tiempo máximo de espera de la respuesta.
 */
@ConfigurationProperties(prefix = "culqi")
public record CulqiProperties(
        @DefaultValue("https://api.culqi.com/v2") String apiUrl,
        @DefaultValue("") String secretKey,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration readTimeout
) {
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
