package com.edifika.payment.payment.domain.services;

import com.edifika.payment.payment.domain.model.aggregates.Debt;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.commands.ConfirmPaymentCommand;
import com.edifika.payment.payment.domain.model.commands.CreateDebtCommand;
import com.edifika.payment.payment.domain.model.commands.MarkOverdueDebtsCommand;
import com.edifika.payment.payment.domain.model.commands.RegisterPaymentCommand;

import java.util.Optional;

/**
 * Contrato de los casos de uso de escritura del bounded context Payment.
 */
public interface PaymentCommandService {

    /**
     * Registra una deuda para una unidad residencial existente.
     */
    Optional<Debt> createDebt(CreateDebtCommand command);

    /**
     * Registra un intento de pago y, si el método lo requiere, lo cobra en la pasarela.
     * Si la clave de idempotencia ya fue usada para el mismo intento, devuelve el pago
     * existente sin volver a cobrar ({@code replayed = true}).
     */
    Optional<RegisterPaymentResult> registerPayment(RegisterPaymentCommand command);

    /**
     * Aprueba o rechaza manualmente un pago registrado o pendiente de verificación.
     */
    Optional<Payment> confirmPayment(ConfirmPaymentCommand command);

    /**
     * Marca como vencidas las deudas pendientes cuya fecha de vencimiento ya pasó.
     *
     * @return cantidad de deudas marcadas.
     */
    int markOverdueDebts(MarkOverdueDebtsCommand command);

    /**
     * Resultado del registro de un pago.
     *
     * @param payment  pago creado o, si es un reintento, el pago existente.
     * @param replayed true si la clave de idempotencia ya existía y no se volvió a cobrar.
     */
    record RegisterPaymentResult(Payment payment, boolean replayed) {
        public static RegisterPaymentResult created(Payment payment) {
            return new RegisterPaymentResult(payment, false);
        }

        public static RegisterPaymentResult replayed(Payment payment) {
            return new RegisterPaymentResult(payment, true);
        }
    }
}
