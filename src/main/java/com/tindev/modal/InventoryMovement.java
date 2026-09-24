package com.tindev.modal;

import com.tindev.domain.InventoryMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Immutable audit trail for inventory quantities. */
@Entity
@Table(name = "inventory_movement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Branch branch;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Product product;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User performedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryMovementType type;

    @Column(nullable = false)
    private Integer quantityBefore;

    @Column(nullable = false)
    private Integer quantityAfter;

    @Column(nullable = false)
    private Integer quantityDelta;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
