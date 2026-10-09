package com.edifika.payment.payment.infrastructure.external.simulated;

import com.edifika.payment.payment.domain.model.valueobjects.PaymentDeclineReason;
import com.edifika.payment.payment.domain.services.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Adaptador del puerto {@link PaymentGateway} que simula la pasarela, para desarrollo
 * y pruebas sin cobros reales.
 * <p>
 * La app envía como token {@code sim_<número de tarjeta de prueba de Culqi>} y el resultado
 * se decide por ese número:
 * <ul>
 *     <li>{@code 4111111111111111} → aprobado</li>
 *     <li>{@code 4000040000000008} → INSUFFICIENT_FUNDS</li>
 *     <li>{@code 4000020000000000} (robada) y {@code 4000030000000009} (perdida) → CARD_NOT_ALLOWED</li>
 *     <li>{@code 5400020000000003} → INCORRECT_CVV</li>
 * </ul>
 * Cualquier otro token se aprueba. Activo solo cuando {@code payment.gateway.provider=simulated}.
 * Nunca usar en producción.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "simulated")
public class SimulatedPaymentGateway implements PaymentGateway {

    static final String TOKEN_PREFIX = "sim_";
    static final String PROVIDER_NAME = "SIMULATED";

    private static final Map<String, PaymentDeclineReason> DECLINED_TEST_CARDS = Map.of(
            "4000040000000008", PaymentDeclineReason.INSUFFICIENT_FUNDS,
            "4000020000000000", PaymentDeclineReason.CARD_NOT_ALLOWED,
            "4000030000000009", PaymentDeclineReason.CARD_NOT_ALLOWED,
            "5400020000000003", PaymentDeclineReason.INCORRECT_CVV
    );

    public SimulatedPaymentGateway() {
        log.warn("Pasarela de pagos SIMULADA activa: no se realizarán cobros reales");
    }

    @Override
    public PaymentGatewayResult charge(ChargeRequest request) {
        String transactionId = "sim_chr_" + UUID.randomUUID();
        String cardNumber = testCardNumber(request.sourceToken());
        PaymentDeclineReason declineReason = cardNumber == null ? null : DECLINED_TEST_CARDS.get(cardNumber);

        if (declineReason != null) {
            return PaymentGatewayResult.declined(transactionId, declineReason, "Pago simulado rechazado: " + declineReason);
        }
        return PaymentGatewayResult.approved(transactionId, "Pago simulado aprobado",
                lastFour(cardNumber), brand(cardNumber));
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    private static String testCardNumber(String token) {
        return (token != null && token.startsWith(TOKEN_PREFIX)) ? token.substring(TOKEN_PREFIX.length()) : null;
    }

    private static String lastFour(String cardNumber) {
        return (cardNumber != null && cardNumber.length() >= 4) ? cardNumber.substring(cardNumber.length() - 4) : null;
    }

    private static String brand(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) return null;
        return switch (cardNumber.charAt(0)) {
            case '4' -> "Visa";
            case '5' -> "MasterCard";
            default -> null;
        };
    }
}
