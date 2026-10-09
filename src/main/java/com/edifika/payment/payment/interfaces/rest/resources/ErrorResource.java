package com.edifika.payment.payment.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Resource de salida uniforme para todas las respuestas de error de la API.
 *
 * @param status    código HTTP.
 * @param error     nombre del estado HTTP (ej. "Conflict").
 * @param message   mensaje legible para el cliente.
 * @param timestamp momento en que ocurrió el error.
 */
@Schema(description = "Respuesta de error")
public record ErrorResource(
        @Schema(example = "409") int status,
        @Schema(example = "Conflict") String error,
        @Schema(example = "La deuda con id 1 ya tiene un pago en proceso o confirmado") String message,
        Instant timestamp
) {
    public static ErrorResource of(HttpStatus status, String message) {
        return new ErrorResource(status.value(), status.getReasonPhrase(), message, Instant.now());
    }
}
