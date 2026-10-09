package com.edifika.payment.payment.application.internal.sagas;

import com.edifika.payment.payment.domain.exceptions.DebtNotFoundException;
import com.edifika.payment.payment.domain.exceptions.PaymentAlreadyInProgressException;
import com.edifika.payment.payment.domain.exceptions.PaymentGatewayException;
import com.edifika.payment.payment.domain.exceptions.PaymentNotFoundException;
import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.commands.ConfirmPaymentCommand;
import com.edifika.payment.payment.domain.model.commands.RegisterPaymentCommand;
import com.edifika.payment.payment.domain.model.entities.PaymentTransaction;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.payment.domain.model.valueobjects.PaymentStatus;
import com.edifika.payment.payment.domain.services.PaymentCommandService.RegisterPaymentResult;
import com.edifika.payment.payment.domain.services.PaymentGateway;
import com.edifika.payment.payment.domain.services.PaymentGateway.ChargeRequest;
import com.edifika.payment.payment.domain.services.PaymentGateway.PaymentGatewayResult;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.DebtRepository;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.edifika.payment.payment.infrastructure.persistence.jpa.repositories.PaymentTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Saga que orquesta la consistencia entre los aggregates Debt y Payment durante un cobro.
 * <p>
 * Pasos para pagos con pasarela (TS07):
 * <ol>
 *     <li><b>Registro</b> (transacción 1): bloquea la deuda, resuelve la idempotencia
 *     (si la clave ya existe devuelve ese pago sin cobrar), valida la deuda y persiste
 *     el Payment en REGISTERED por el monto de la deuda en BD.</li>
 *     <li><b>Cobro</b> (sin transacción): invoca la pasarela, evitando mantener conexiones
 *     de base de datos abiertas durante la llamada HTTP externa.</li>
 *     <li><b>Cierre</b> (transacción 2):
 *     <ul>
 *         <li>Aprobado: confirma el Payment y marca la Debt como PAID de forma atómica.</li>
 *         <li>Rechazado o error técnico claro: rechaza el Payment con su motivo y deja la
 *         Debt intacta y libre (compensación - Escenario 3).</li>
 *         <li>Resultado desconocido (timeout): deja el Payment en PENDING_VERIFICATION,
 *         la Debt queda bloqueada y se responde 502 hasta que un ADMIN lo resuelva.</li>
 *     </ul></li>
 * </ol>
 * Los pagos manuales (voucher, transferencia) terminan en el paso 1 y se cierran luego
 * con {@link #confirmManually(ConfirmPaymentCommand)}.
 * <p>
 * No depende de Culqi directamente, solo del puerto {@link PaymentGateway}.
 */
@Slf4j
@Component
public class PaymentSaga {

    static final String GATEWAY_UNAVAILABLE_MESSAGE = "Error técnico de la pasarela de pago";
    static final String OUTCOME_UNKNOWN_MESSAGE = "La pasarela no respondió a tiempo; resultado del cargo desconocido";

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    public PaymentSaga(DebtRepository debtRepository,
                       PaymentRepository paymentRepository,
                       PaymentTransactionRepository paymentTransactionRepository,
                       PaymentGateway paymentGateway,
                       PlatformTransactionManager transactionManager) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentGateway = paymentGateway;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Ejecuta el saga completo de registro de pago.
     *
     * @return el Payment en su estado final (CONFIRMED/REJECTED), REGISTERED si espera revisión
     * manual, o el pago existente si la clave de idempotencia ya se había usado.
     * @throws PaymentGatewayException si el resultado del cargo es desconocido; el pago queda
     *                                 en PENDING_VERIFICATION.
     */
    public RegisterPaymentResult execute(RegisterPaymentCommand command) {
        Attempt attempt = inTransaction(() -> registerAttempt(command));
        if (attempt.replayed()) {
            return RegisterPaymentResult.replayed(attempt.payment());
        }
        if (!command.requiresGateway()) {
            return RegisterPaymentResult.created(attempt.payment());
        }
        return RegisterPaymentResult.created(chargeAndClose(attempt, command));
    }

    /**
     * Aprueba o rechaza manualmente un pago REGISTERED o PENDING_VERIFICATION
     * (ej. admin valida un voucher - US22/US23, o resuelve un timeout de la pasarela).
     */
    public Payment confirmManually(ConfirmPaymentCommand command) {
        return inTransaction(() -> {
            Payment payment = findPayment(command.paymentId());
            if (command.approved()) {
                return completePayment(payment, command.provider(), command.providerTransactionId(),
                        command.responseMessage(), null, null);
            }
            return rejectPayment(payment, command.provider(), command.providerTransactionId(),
                    PaymentDeclineReason.GENERIC_DECLINE, command.responseMessage());
        });
    }

    // ---------- Paso 1: registro ----------

    private Attempt registerAttempt(RegisterPaymentCommand command) {
        Debt debt = debtRepository.findWithLockById(command.debtId())
                .orElseThrow(() -> new DebtNotFoundException(command.debtId()));

        var existing = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return Attempt.replayed(ensureSameAttempt(existing.get(), command));
        }

        debt.ensureCanBePaid();
        if (paymentRepository.existsByDebtIdAndStatusIn(debt.getId(), PaymentStatus.activeStatuses())) {
            throw new PaymentAlreadyInProgressException(debt.getId());
        }
        Payment payment = paymentRepository.save(new Payment(command, debt.getAmount()));
        return Attempt.created(payment, debt.getUnitId());
    }

    private Payment ensureSameAttempt(Payment existing, RegisterPaymentCommand command) {
        if (!existing.isSameAttemptAs(command)) {
            throw new IllegalStateException("La Idempotency-Key ya fue utilizada para otro pago");
        }
        log.info("Reintento con Idempotency-Key ya usada: se devuelve el pago {} sin volver a cobrar", existing.getId());
        return existing;
    }

    // ---------- Paso 2 y 3: cobro y cierre ----------

    private Payment chargeAndClose(Attempt attempt, RegisterPaymentCommand command) {
        Payment payment = attempt.payment();
        PaymentGatewayResult result;
        try {
            result = paymentGateway.charge(buildChargeRequest(attempt, command));
        } catch (PaymentGatewayException ex) {
            if (ex.isOutcomeUnknown()) {
                throw leavePendingVerification(payment.getId(), ex);
            }
            log.warn("Error técnico de la pasarela para el pago {}: {}", payment.getId(), ex.getMessage());
            result = PaymentGatewayResult.declined(null, PaymentDeclineReason.GATEWAY_ERROR, GATEWAY_UNAVAILABLE_MESSAGE);
        } catch (RuntimeException ex) {
            throw leavePendingVerification(payment.getId(), ex);
        }
        return closeWithGatewayResult(payment.getId(), result);
    }

    private ChargeRequest buildChargeRequest(Attempt attempt, RegisterPaymentCommand command) {
        Payment payment = attempt.payment();
        return new ChargeRequest(
                command.gatewayToken(),
                payment.getAmount(),
                command.payerEmail(),
                "Edifika - Pago de deuda #" + payment.getDebtId(),
                payment.getId(),
                payment.getDebtId(),
                attempt.unitId()
        );
    }

    private Payment closeWithGatewayResult(Long paymentId, PaymentGatewayResult result) {
        String provider = paymentGateway.providerName();
        try {
            return inTransaction(() -> {
                Payment payment = findPayment(paymentId);
                return result.approved()
                        ? completePayment(payment, provider, result.providerTransactionId(), result.responseMessage(),
                                result.cardLastFour(), result.cardBrand())
                        : rejectPayment(payment, provider, result.providerTransactionId(),
                                result.declineReason(), result.responseMessage());
            });
        } catch (RuntimeException ex) {
            if (result.approved()) {
                log.error("INCONSISTENCIA: el cargo {} fue aprobado por {} pero no se pudo confirmar el pago {}. "
                        + "Requiere conciliación manual.", result.providerTransactionId(), provider, paymentId, ex);
            }
            throw ex;
        }
    }

    /**
     * Deja el pago en PENDING_VERIFICATION y devuelve la excepción que se responde al cliente (502).
     */
    private PaymentGatewayException leavePendingVerification(Long paymentId, Exception cause) {
        log.error("Resultado desconocido del cargo para el pago {}: queda PENDING_VERIFICATION. Causa: {}",
                paymentId, cause.getMessage(), cause);
        inTransaction(() -> {
            Payment payment = findPayment(paymentId);
            PaymentTransaction transaction = payment.markPendingVerification(paymentGateway.providerName(), OUTCOME_UNKNOWN_MESSAGE);
            paymentTransactionRepository.save(transaction);
            return paymentRepository.save(payment);
        });
        return new PaymentGatewayException("No se pudo confirmar el resultado del pago " + paymentId
                + " con la pasarela. El pago quedó en verificación; no lo intente de nuevo.");
    }

    private Payment completePayment(Payment payment, String provider, String providerTransactionId, String message,
                                    String cardLastFour, String cardBrand) {
        PaymentTransaction transaction = payment.confirm(provider, providerTransactionId, message);
        transaction.recordCard(cardLastFour, cardBrand);
        Debt debt = debtRepository.findById(payment.getDebtId())
                .orElseThrow(() -> new DebtNotFoundException(payment.getDebtId()));
        debt.markAsPaid();

        paymentTransactionRepository.save(transaction);
        debtRepository.save(debt);
        log.info("Pago {} confirmado; deuda {} marcada como PAID", payment.getId(), debt.getId());
        return paymentRepository.save(payment);
    }

    private Payment rejectPayment(Payment payment, String provider, String providerTransactionId,
                                  PaymentDeclineReason reason, String message) {
        PaymentTransaction transaction = payment.reject(provider, providerTransactionId, reason, message);
        paymentTransactionRepository.save(transaction);
        log.warn("Pago {} rechazado para la deuda {}: {} ({})", payment.getId(), payment.getDebtId(), reason, message);
        return paymentRepository.save(payment);
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    /**
     * Resultado interno del paso 1: el pago y los datos de la deuda necesarios para el cobro.
     */
    private record Attempt(Payment payment, Long unitId, boolean replayed) {
        static Attempt created(Payment payment, Long unitId) {
            return new Attempt(payment, unitId, false);
        }

        static Attempt replayed(Payment payment) {
            return new Attempt(payment, null, true);
        }
    }
}
