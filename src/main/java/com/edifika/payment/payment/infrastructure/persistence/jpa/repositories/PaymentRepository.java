package com.edifika.payment.payment.infrastructure.persistence.jpa.repositories;

import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA del aggregate {@link Payment}.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserId(Long userId);

    List<Payment> findByUserIdAndPaymentDateBetween(Long userId, Date startDate, Date endDate);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByDebtIdAndStatusIn(Long debtId, Collection<PaymentStatus> statuses);

    List<Payment> findByStatusOrderByCreatedAtAsc(PaymentStatus status);

    List<Payment> findByStatusAndPaymentMethodInOrderByCreatedAtAsc(PaymentStatus status, Collection<String> paymentMethods);
}
