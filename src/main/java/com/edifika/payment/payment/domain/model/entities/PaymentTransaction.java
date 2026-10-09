package com.edifika.payment.payment.domain.model.entities;

import com.edifika.payment.payment.domain.model.valueobjects.TransactionStatus;
import com.edifika.payment.shared.domain.model.entity.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;

/**
 * Entidad que representa el registro técnico de la transacción
 * realizada con el proveedor externo (Culqi) u otro método (manual, voucher).
 * Solo se modifica a través del aggregate Payment.
 * <p>
 * Nunca almacena el número de tarjeta ni el CVV: de la tarjeta solo guarda
 * los últimos 4 dígitos y la marca, cuando el proveedor los informa.
 */
@Getter
@Entity
public class PaymentTransaction extends AuditableModel {

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private String provider;

    private String providerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private String responseMessage;

    @Column(length = 4)
    private String cardLastFour;

    @Column(length = 30)
    private String cardBrand;

    protected PaymentTransaction() {}

    public PaymentTransaction(Long paymentId, String provider, String providerTransactionId, String responseMessage) {
        this.paymentId = paymentId;
        this.provider = provider;
        this.providerTransactionId = providerTransactionId;
        this.responseMessage = responseMessage;
        this.status = TransactionStatus.PENDING;
    }

    public void approve() {
        this.status = TransactionStatus.SUCCESS;
    }

    public void fail(String reason) {
        this.status = TransactionStatus.FAILED;
        this.responseMessage = reason;
    }

    /**
     * Registra los datos no sensibles de la tarjeta. Descarta los últimos dígitos
     * si no son exactamente 4 dígitos, para no persistir nunca un número de tarjeta.
     */
    public void recordCard(String lastFour, String brand) {
        this.cardLastFour = (lastFour != null && lastFour.matches("\\d{4}")) ? lastFour : null;
        this.cardBrand = (brand == null || brand.isBlank()) ? null : brand.trim();
    }
}