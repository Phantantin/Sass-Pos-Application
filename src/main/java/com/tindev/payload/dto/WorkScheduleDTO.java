package com.tindev.payload.dto;

import com.tindev.domain.WorkScheduleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class WorkScheduleDTO {
    private Long id;
    @NotNull private Long employeeId;
    private String employeeName;
    @NotNull private Long branchId;
    private String branchName;
    @NotNull private LocalDate workDate;
    @NotNull private LocalTime startTime;
    @NotNull private LocalTime endTime;
    private WorkScheduleStatus status;
    @Size(max = 500) private String note;
}
