package com.edifika.payment.payment.application.internal.outboundservices.acl;

import com.edifika.payment.payment.domain.exceptions.ExternalServiceUnavailableException;

/**
 * Anti-corruption layer (puerto de salida) hacia el microservicio responsable
 * de las unidades residenciales. Aísla al bounded context Payment del modelo
 * y del protocolo de ese otro servicio.
 */
public interface ExternalUnitService {

    /**
     * @return true si la unidad existe.
     * @throws ExternalServiceUnavailableException si el servicio de unidades no responde.
     */
    boolean existsUnitById(Long unitId);
}
