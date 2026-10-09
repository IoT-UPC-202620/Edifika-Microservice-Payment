package com.edifika.payment.payment.domain.model.commands;

/**
 * Comando para que un administrador apruebe o rechace manualmente un pago registrado
 * (ej. validación de un voucher subido por el residente - US22/US23).
 * Si {@code provider} viene vacío se usa el método de pago original como proveedor.
 */
public record ConfirmPaymentCommand(
        Long paymentId,
        boolean approved,
        String provider,
        String providerTransactionId,
        String responseMessage
) {
    public ConfirmPaymentCommand {
        if (paymentId == null) throw new IllegalArgumentException("El pago es obligatorio");
    }
}
