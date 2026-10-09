package com.edifika.payment.payment.application.internal.queryservices;

import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.queries.*;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentMethod;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentStatus;
import com.edifika.payment.payment.domain.services.PaymentQueryService;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.DebtRepository;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implementación de los casos de uso de lectura del bounded context Payment.
 */
@Service
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;

    public PaymentQueryServiceImpl(DebtRepository debtRepository, PaymentRepository paymentRepository) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Optional<Debt> getDebtById(GetDebtByIdQuery query) {
        return debtRepository.findById(query.debtId());
    }

    @Override
    public List<Debt> getDebtsByUnit(GetDebtsByUnitQuery query) {
        return debtRepository.findByUnitId(query.unitId());
    }

    @Override
    public Optional<Payment> getPaymentById(GetPaymentByIdQuery query) {
        return paymentRepository.findById(query.paymentId());
    }

    @Override
    public List<Payment> getPaymentsByUser(GetPaymentsByUserQuery query) {
        return paymentRepository.findByUserId(query.userId());
    }

    @Override
    public List<Payment> getPaymentHistoryByYear(GetPaymentHistoryByYearQuery query) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(query.year(), Calendar.JANUARY, 1, 0, 0, 0);
        Date startDate = calendar.getTime();

        calendar.set(query.year(), Calendar.DECEMBER, 31, 23, 59, 59);
        Date endDate = calendar.getTime();

        return paymentRepository.findByUserIdAndPaymentDateBetween(query.userId(), startDate, endDate);
    }

    /**
     * Pagos que un ADMIN debe resolver, del más antiguo al más reciente:
     * pagos manuales registrados (voucher, transferencia) y pagos con resultado
     * desconocido de la pasarela (PENDING_VERIFICATION).
     */
    @Override
    public List<Payment> getPendingReviewPayments(GetPendingReviewPaymentsQuery query) {
        List<Payment> manualPayments = paymentRepository.findByStatusAndPaymentMethodInOrderByCreatedAtAsc(
                PaymentStatus.REGISTERED, PaymentMethod.manualReviewMethodNames());
        List<Payment> pendingVerification = paymentRepository.findByStatusOrderByCreatedAtAsc(
                PaymentStatus.PENDING_VERIFICATION);
        return Stream.concat(manualPayments.stream(), pendingVerification.stream())
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
