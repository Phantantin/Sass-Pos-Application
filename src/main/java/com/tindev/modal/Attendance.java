package com.tindev.modal;

import com.tindev.domain.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "attendance", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
public class Attendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User employee;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Branch branch;
    @Column(nullable = false)
    private LocalDate workDate;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AttendanceStatus status;
    @Column(length = 500)
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist void create() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = AttendanceStatus.PRESENT;
    }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
