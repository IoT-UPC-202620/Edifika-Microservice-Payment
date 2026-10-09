package com.edifika.payment.payment.interfaces.rest.exceptions;

import com.edifika.payment.payment.domain.exceptions.*;
import com.edifika.payment.payment.interfaces.rest.resources.ErrorResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejador global de excepciones del microservicio Payment.
 * Traduce las excepciones de dominio y de infraestructura en respuestas HTTP
 * coherentes con los escenarios de TS07, con un cuerpo uniforme {@link ErrorResource}
 * y sin exponer detalles internos de implementación al cliente.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404: la deuda, el pago o la unidad no existen. */
    @ExceptionHandler({DebtNotFoundException.class, PaymentNotFoundException.class, UnitNotFoundException.class})
    public ResponseEntity<ErrorResource> handleNotFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** 400: datos de entrada inválidos (validaciones de commands, queries y value objects). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResource> handleBadRequest(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** 400: cuerpo JSON mal formado o parámetro de ruta con tipo incorrecto. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResource> handleMalformedRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "La solicitud tiene un formato inválido");
    }

    /** 409: la operación no es válida en el estado actual (deuda pagada, pago duplicado, transición inválida). */
    @ExceptionHandler({DebtAlreadyPaidException.class, PaymentAlreadyInProgressException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResource> handleConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * 409: dos solicitudes simultáneas con el mismo Idempotency-Key chocaron con la restricción única;
     * el cliente puede reintentar y recibirá el pago existente.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResource> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "La solicitud entra en conflicto con otro pago en curso; reintente en unos segundos");
    }

    /** 401: el token no permite identificar al usuario. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResource> handleUnauthorized(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** 403: el usuario no tiene el rol requerido o intenta acceder a datos de otro usuario. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResource> handleForbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta operación");
    }

    /**
     * 502: la pasarela no respondió a tiempo y no se sabe si cobró. El pago quedó en
     * PENDING_VERIFICATION, la deuda queda bloqueada y un ADMIN lo resolverá.
     */
    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ErrorResource> handlePaymentGateway(PaymentGatewayException ex) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    /** 503: otro microservicio necesario para la validación no está disponible. */
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ErrorResource> handleServiceUnavailable(ExternalServiceUnavailableException ex) {
        log.warn(ex.getMessage(), ex.getCause());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    /** 500: error no previsto. Se registra el detalle y se responde un mensaje genérico. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResource> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado, intente nuevamente");
    }

    private static ResponseEntity<ErrorResource> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResource.of(status, message));
    }
}
