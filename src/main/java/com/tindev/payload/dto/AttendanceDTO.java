package com.tindev.payload.dto;

import com.tindev.domain.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AttendanceDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    @NotNull private Long branchId;
    private String branchName;
    private LocalDate workDate;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private AttendanceStatus status;
    @Size(max = 500) private String note;
}
