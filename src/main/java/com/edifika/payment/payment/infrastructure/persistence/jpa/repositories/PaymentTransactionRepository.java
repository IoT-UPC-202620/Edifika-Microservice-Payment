package com.edifika.payment.payment.infrastructure.persistence.jpa.repositories;

import com.edifika.payment.payment.domain.model.entities.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA de {@link PaymentTransaction}: registro técnico de cada
 * interacción con el proveedor de pagos (aprobaciones, rechazos, ids de cargo).
 */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
}
