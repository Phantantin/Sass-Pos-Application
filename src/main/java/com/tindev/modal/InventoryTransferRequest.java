package com.tindev.modal;

import com.tindev.domain.InventoryTransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransferRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Product product;

    @ManyToOne(optional = false)
    private Branch sourceBranch;

    @ManyToOne(optional = false)
    private Branch destinationBranch;

    @Column(nullable = false)
    private Integer quantity;

    @ManyToOne(optional = false)
    private User requestedBy;

    @ManyToOne
    private User reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryTransferStatus status;

    @Column(length = 500)
    private String reason;

    @Column(length = 500)
    private String reviewNote;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = InventoryTransferStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
