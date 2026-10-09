package com.edifika.payment.payment.domain.exceptions;

/**
 * Excepción lanzada cuando la unidad residencial indicada no existe
 * en el microservicio responsable de las unidades.
 */
public class UnitNotFoundException extends RuntimeException {
    public UnitNotFoundException(Long unitId) {
        super("No se encontró la unidad con id: " + unitId);
    }
}
