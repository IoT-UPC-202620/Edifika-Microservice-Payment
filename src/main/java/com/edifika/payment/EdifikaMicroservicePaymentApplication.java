package com.edifika.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del microservicio Payment de Edifika.
 * Habilita la auditoría JPA, las tareas programadas (vencimiento de deudas)
 * y el registro de las clases {@code @ConfigurationProperties}.
 */
@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class EdifikaMicroservicePaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdifikaMicroservicePaymentApplication.class, args);
    }

}
