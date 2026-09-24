package com.tindev.modal;

import com.tindev.domain.WorkScheduleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "work_schedule", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
public class WorkSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User employee;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Branch branch;
    @Column(nullable = false)
    private LocalDate workDate;
    @Column(nullable = false)
    private LocalTime startTime;
    @Column(nullable = false)
    private LocalTime endTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private WorkScheduleStatus status;
    @Column(length = 500)
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist void create() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = WorkScheduleStatus.SCHEDULED;
    }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
