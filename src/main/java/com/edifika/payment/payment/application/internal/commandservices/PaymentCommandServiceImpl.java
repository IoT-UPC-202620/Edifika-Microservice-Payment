package com.edifika.payment.payment.application.internal.commandservices;

import com.edifika.payment.payment.application.internal.outboundservices.acl.ExternalUnitService;
import com.edifika.payment.payment.application.internal.sagas.PaymentSaga;
import com.edifika.payment.payment.domain.exceptions.UnitNotFoundException;
import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.commands.ConfirmPaymentCommand;
import com.edifika.payment.payment.domain.model.commands.CreateDebtCommand;
import com.edifika.payment.payment.domain.model.commands.MarkOverdueDebtsCommand;
import com.edifika.payment.payment.domain.model.commands.RegisterPaymentCommand;
import com.edifika.payment.payment.domain.model.valueobjects.DebtStatus;
import com.edifika.payment.payment.domain.services.PaymentCommandService;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.DebtRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementación de los casos de uso de escritura del bounded context Payment.
 * Delega el flujo de cobro en {@link PaymentSaga} y valida las unidades
 * contra su microservicio a través de la ACL {@link ExternalUnitService}.
 */
@Service
public class PaymentCommandServiceImpl implements PaymentCommandService {

    private final DebtRepository debtRepository;
    private final PaymentSaga paymentSaga;
    private final ExternalUnitService externalUnitService;

    public PaymentCommandServiceImpl(DebtRepository debtRepository,
                                     PaymentSaga paymentSaga,
                                     ExternalUnitService externalUnitService) {
        this.debtRepository = debtRepository;
        this.paymentSaga = paymentSaga;
        this.externalUnitService = externalUnitService;
    }

    @Override
    public Optional<Debt> createDebt(CreateDebtCommand command) {
        if (!externalUnitService.existsUnitById(command.unitId())) {
            throw new UnitNotFoundException(command.unitId());
        }
        return Optional.of(debtRepository.save(new Debt(command)));
    }

    @Override
    public Optional<RegisterPaymentResult> registerPayment(RegisterPaymentCommand command) {
        return Optional.of(paymentSaga.execute(command));
    }

    @Override
    public Optional<Payment> confirmPayment(ConfirmPaymentCommand command) {
        return Optional.of(paymentSaga.confirmManually(command));
    }

    @Override
    @Transactional
    public int markOverdueDebts(MarkOverdueDebtsCommand command) {
        List<Debt> overdueDebts = debtRepository.findByStatusAndDueDateBefore(DebtStatus.PENDING, command.referenceDate());
        overdueDebts.forEach(Debt::markAsOverdue);
        debtRepository.saveAll(overdueDebts);
        return overdueDebts.size();
    }
}
