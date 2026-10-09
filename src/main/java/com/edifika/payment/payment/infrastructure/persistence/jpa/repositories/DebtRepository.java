package com.edifika.payment.payment.infrastructure.persistence.jpa.repositories;

import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.valueobjects.DebtStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA del aggregate {@link Debt}.
 */
public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findByUnitId(Long unitId);

    /**
     * Obtiene la deuda con bloqueo de escritura (SELECT ... FOR UPDATE) para
     * serializar pagos concurrentes sobre la misma deuda. Requiere una transacción activa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Debt> findWithLockById(Long id);

    List<Debt> findByStatusAndDueDateBefore(DebtStatus status, LocalDate date);
}
