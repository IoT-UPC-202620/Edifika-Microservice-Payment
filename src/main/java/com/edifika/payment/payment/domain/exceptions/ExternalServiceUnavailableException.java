package com.edifika.payment.payment.domain.exceptions;

/**
 * Excepción lanzada cuando otro microservicio de Edifika del que depende
 * una validación no responde o responde con un error inesperado.
 */
public class ExternalServiceUnavailableException extends RuntimeException {
    public ExternalServiceUnavailableException(String serviceName, Throwable cause) {
        super("El servicio " + serviceName + " no está disponible en este momento", cause);
    }

    public ExternalServiceUnavailableException(String serviceName, String detail) {
        super("El servicio " + serviceName + " no está disponible en este momento: " + detail);
    }
}
