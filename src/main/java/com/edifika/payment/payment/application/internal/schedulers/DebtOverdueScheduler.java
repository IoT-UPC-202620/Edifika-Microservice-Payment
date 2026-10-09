package com.edifika.payment.payment.application.internal.schedulers;

import com.edifika.payment.payment.domain.model.commands.MarkOverdueDebtsCommand;
import com.edifika.payment.payment.domain.services.PaymentCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Proceso programado que marca como OVERDUE las deudas pendientes vencidas.
 * La frecuencia se configura con {@code payment.debts.overdue-check-cron}
 * y la zona horaria con {@code payment.time-zone}.
 */
@Slf4j
@Component
public class DebtOverdueScheduler {

    private final PaymentCommandService paymentCommandService;
    private final ZoneId zoneId;

    public DebtOverdueScheduler(PaymentCommandService paymentCommandService,
                                @Value("${payment.time-zone:America/Lima}") String timeZone) {
        this.paymentCommandService = paymentCommandService;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Scheduled(cron = "${payment.debts.overdue-check-cron:0 0 1 * * *}", zone = "${payment.time-zone:America/Lima}")
    public void markOverdueDebts() {
        int marked = paymentCommandService.markOverdueDebts(new MarkOverdueDebtsCommand(LocalDate.now(zoneId)));
        log.info("Proceso de vencimiento ejecutado: {} deuda(s) marcadas como OVERDUE", marked);
    }
}
