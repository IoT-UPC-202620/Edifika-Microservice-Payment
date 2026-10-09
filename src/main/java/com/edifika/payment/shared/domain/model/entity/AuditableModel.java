package com.edifika.payment.shared.domain.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * Clase base para entidades (no aggregate roots) que requieren identificador
 * y auditoría automática de fechas de creación y última modificación.
 */
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public class AuditableModel {

    @Id
    @Getter
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Date createdAt;

    @Getter
    @LastModifiedDate
    @Column(nullable = false)
    private Date updatedAt;
}
