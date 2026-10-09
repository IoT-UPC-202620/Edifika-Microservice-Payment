package com.edifika.payment.payment.domain.services;

import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de los casos de uso de lectura del bounded context Payment.
 */
public interface PaymentQueryService {

    Optional<Debt> getDebtById(GetDebtByIdQuery query);

    List<Debt> getDebtsByUnit(GetDebtsByUnitQuery query);

    Optional<Payment> getPaymentById(GetPaymentByIdQuery query);

    List<Payment> getPaymentsByUser(GetPaymentsByUserQuery query);

    List<Payment> getPaymentHistoryByYear(GetPaymentHistoryByYearQuery query);

    List<Payment> getPendingReviewPayments(GetPendingReviewPaymentsQuery query);
}
