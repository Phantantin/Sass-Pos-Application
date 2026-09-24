package com.tindev.service.impl;

import com.tindev.domain.AttendanceStatus;
import com.tindev.domain.PayrollStatus;
import com.tindev.domain.UserRole;
import com.tindev.domain.WorkScheduleStatus;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.modal.*;
import com.tindev.payload.dto.AttendanceDTO;
import com.tindev.payload.dto.PayrollDTO;
import com.tindev.payload.dto.WorkScheduleDTO;
import com.tindev.repository.*;
import com.tindev.service.UserService;
import com.tindev.service.WorkforceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WorkforceServiceImpl implements WorkforceService {
    private static final int MAX_DAYS = 366;
    private final UserService userService;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final AttendanceRepository attendanceRepository;
    private final PayrollRepository payrollRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WorkScheduleDTO> schedules(Long requestedStoreId, Long requestedBranchId, LocalDate requestedFrom, LocalDate requestedTo) {
        Range range = range(requestedFrom, requestedTo);
        User actor = currentUser();
        if (isEmployeeSelfOnly(actor)) {
            return scheduleRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescStartTimeAsc(actor.getId(), range.from(), range.to()).stream().map(this::scheduleDto).toList();
        }
        WorkforceScope scope = scope(actor, requestedStoreId, requestedBranchId);
        List<WorkSchedule> records = scope.branchId() == null
                ? scheduleRepository.findByBranchStoreIdAndWorkDateBetweenOrderByWorkDateDescStartTimeAsc(scope.storeId(), range.from(), range.to())
                : scheduleRepository.findByBranchIdAndWorkDateBetweenOrderByWorkDateDescStartTimeAsc(scope.branchId(), range.from(), range.to());
        return records.stream().map(this::scheduleDto).toList();
    }

    @Override
    @Transactional
    public WorkScheduleDTO saveSchedule(Long id, WorkScheduleDTO request) {
        User actor = currentUser();
        Branch branch = requiredBranch(request.getBranchId());
        requireOperationsManager(actor, branch);
        User employee = requiredEmployee(request.getEmployeeId());
        requireEmployeeInBranch(employee, branch);
        if (request.getEndTime() == null || request.getStartTime() == null || !request.getEndTime().isAfter(request.getStartTime())) {
            throw ApiException.badRequest("Giờ kết thúc phải sau giờ bắt đầu");
        }
        WorkSchedule record = id == null ? new WorkSchedule() : scheduleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lịch làm việc"));
        if (id != null) requireOperationsManager(actor, record.getBranch());
        record.setEmployee(employee);
        record.setBranch(branch);
        record.setWorkDate(request.getWorkDate());
        record.setStartTime(request.getStartTime());
        record.setEndTime(request.getEndTime());
        record.setStatus(request.getStatus() == null ? WorkScheduleStatus.SCHEDULED : request.getStatus());
        record.setNote(trim(request.getNote()));
        return scheduleDto(scheduleRepository.save(record));
    }

    @Override
    @Transactional
    public void deleteSchedule(Long id) {
        WorkSchedule record = scheduleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lịch làm việc"));
        requireOperationsManager(currentUser(), record.getBranch());
        scheduleRepository.delete(record);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceDTO> attendance(Long requestedStoreId, Long requestedBranchId, LocalDate requestedFrom, LocalDate requestedTo) {
        Range range = range(requestedFrom, requestedTo);
        User actor = currentUser();
        if (isEmployeeSelfOnly(actor)) {
            return attendanceRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(actor.getId(), range.from(), range.to()).stream().map(this::attendanceDto).toList();
        }
        WorkforceScope scope = scope(actor, requestedStoreId, requestedBranchId);
        List<Attendance> records = scope.branchId() == null
                ? attendanceRepository.findByBranchStoreIdAndWorkDateBetweenOrderByWorkDateDesc(scope.storeId(), range.from(), range.to())
                : attendanceRepository.findByBranchIdAndWorkDateBetweenOrderByWorkDateDesc(scope.branchId(), range.from(), range.to());
        return records.stream().map(this::attendanceDto).toList();
    }

    @Override
    @Transactional
    public AttendanceDTO checkIn(AttendanceDTO request) {
        User actor = currentUser();
        Branch branch = requiredBranch(request.getBranchId());
        requireSelfBranch(actor, branch);
        LocalDate today = LocalDate.now();
        Attendance record = attendanceRepository.findByEmployeeIdAndWorkDate(actor.getId(), today).orElseGet(Attendance::new);
        if (record.getCheckIn() != null) throw ApiException.conflict("Bạn đã chấm công vào ca hôm nay");
        record.setEmployee(actor);
        record.setBranch(branch);
        record.setWorkDate(today);
        record.setCheckIn(LocalDateTime.now());
        record.setStatus(AttendanceStatus.PRESENT);
        record.setNote(trim(request.getNote()));
        return attendanceDto(attendanceRepository.save(record));
    }

    @Override
    @Transactional
    public AttendanceDTO checkOut() {
        User actor = currentUser();
        Attendance record = attendanceRepository.findByEmployeeIdAndWorkDate(actor.getId(), LocalDate.now())
                .orElseThrow(() -> ApiException.conflict("Bạn chưa chấm công vào ca hôm nay"));
        if (record.getCheckOut() != null) throw ApiException.conflict("Bạn đã chấm công kết thúc hôm nay");
        record.setCheckOut(LocalDateTime.now());
        return attendanceDto(attendanceRepository.save(record));
    }

    @Override
    @Transactional
    public AttendanceDTO updateAttendance(Long id, AttendanceDTO request) {
        Attendance record = attendanceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bản ghi chấm công"));
        requireOperationsManager(currentUser(), record.getBranch());
        if (request.getStatus() != null) record.setStatus(request.getStatus());
        record.setNote(trim(request.getNote()));
        return attendanceDto(attendanceRepository.save(record));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollDTO> payrolls(Long requestedStoreId, LocalDate requestedFrom, LocalDate requestedTo) {
        Range range = range(requestedFrom, requestedTo);
        User actor = currentUser();
        if (actor.getRole() == UserRole.ROLE_BRANCH_MANAGER || actor.getRole() == UserRole.ROLE_BRANCH_CASHIER) {
            return payrollRepository.findByEmployeeIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodEndDesc(actor.getId(), range.from(), range.to()).stream().map(this::payrollDto).toList();
        }
        Store store = resolveStore(actor, requestedStoreId);
        return payrollRepository.findByStoreIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodEndDesc(store.getId(), range.from(), range.to()).stream().map(this::payrollDto).toList();
    }

    @Override
    @Transactional
    public PayrollDTO savePayroll(Long id, PayrollDTO request) {
        User actor = currentUser();
        if (!canManagePayroll(actor)) throw ApiException.forbidden("Bạn không có quyền lập bảng lương");
        User employee = requiredEmployee(request.getEmployeeId());
        Store store = storeFor(employee);
        requireStoreAccess(actor, store);
        if (request.getPeriodStart().isAfter(request.getPeriodEnd())) throw ApiException.badRequest("Kỳ lương không hợp lệ");
        Payroll record = id == null ? new Payroll() : payrollRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bảng lương"));
        if (id != null && record.getStatus() == PayrollStatus.PAID) throw ApiException.conflict("Không thể sửa bảng lương đã chi trả");
        BigDecimal hourlyRate = nonNegative(request.getHourlyRate(), "Đơn giá theo giờ");
        int workedMinutes = calculateWorkedMinutes(employee.getId(), request.getPeriodStart(), request.getPeriodEnd());
        BigDecimal base = hourlyRate.signum() > 0
                ? hourlyRate.multiply(BigDecimal.valueOf(workedMinutes))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                : nonNegative(request.getBaseSalary(), "Lương cơ bản");
        BigDecimal bonus = nonNegative(request.getBonus(), "Thưởng");
        BigDecimal deduction = nonNegative(request.getDeduction(), "Khấu trừ");
        BigDecimal net = base.add(bonus).subtract(deduction);
        if (net.signum() < 0) throw ApiException.badRequest("Lương thực nhận không được âm");
        record.setEmployee(employee);
        record.setStore(store);
        record.setPeriodStart(request.getPeriodStart());
        record.setPeriodEnd(request.getPeriodEnd());
        record.setBaseSalary(base);
        record.setHourlyRate(hourlyRate);
        record.setWorkedMinutes(workedMinutes);
        record.setBonus(bonus);
        record.setDeduction(deduction);
        record.setNetSalary(net);
        record.setStatus(request.getStatus() == PayrollStatus.APPROVED ? PayrollStatus.APPROVED : PayrollStatus.DRAFT);
        record.setNote(trim(request.getNote()));
        return payrollDto(payrollRepository.save(record));
    }

    @Override
    @Transactional
    public PayrollDTO pay(Long id) {
        Payroll record = payrollRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy bảng lương"));
        User actor = currentUser();
        if (actor.getRole() != UserRole.ROLE_ADMIN && actor.getRole() != UserRole.ROLE_STORE_ADMIN) {
            throw ApiException.forbidden("Chỉ HQ hoặc quản trị cửa hàng được xác nhận chi lương");
        }
        requireStoreAccess(actor, record.getStore());
        if (record.getStatus() == PayrollStatus.PAID) throw ApiException.conflict("Bảng lương đã được chi trả");
        record.setStatus(PayrollStatus.PAID);
        record.setPaidAt(LocalDateTime.now());
        return payrollDto(payrollRepository.save(record));
    }

    private WorkforceScope scope(User actor, Long requestedStoreId, Long requestedBranchId) {
        if (actor.getRole() == UserRole.ROLE_BRANCH_MANAGER) {
            if (actor.getBranch() == null) throw ApiException.forbidden("Tài khoản chưa được phân công chi nhánh");
            return new WorkforceScope(actor.getBranch().getStore().getId(), actor.getBranch().getId());
        }
        Store store = resolveStore(actor, requestedStoreId);
        if (requestedBranchId == null) return new WorkforceScope(store.getId(), null);
        Branch branch = requiredBranch(requestedBranchId);
        if (!Objects.equals(branch.getStore().getId(), store.getId())) throw ApiException.forbidden("Chi nhánh không thuộc cửa hàng được chọn");
        return new WorkforceScope(store.getId(), branch.getId());
    }

    private Store resolveStore(User actor, Long requestedStoreId) {
        if (actor.getRole() == UserRole.ROLE_ADMIN) {
            if (requestedStoreId == null) throw ApiException.badRequest("HQ cần chọn cửa hàng");
            return requiredStore(requestedStoreId);
        }
        if (requestedStoreId != null) {
            Store requested = requiredStore(requestedStoreId);
            requireStoreAccess(actor, requested);
            return requested;
        }
        return storeFor(actor);
    }

    private void requireOperationsManager(User actor, Branch branch) {
        if (actor.getRole() == UserRole.ROLE_ADMIN) return;
        requireStoreAccess(actor, branch.getStore());
        if (actor.getRole() == UserRole.ROLE_STORE_ADMIN || actor.getRole() == UserRole.ROLE_STORE_MANAGER) return;
        if (actor.getRole() == UserRole.ROLE_BRANCH_MANAGER && actor.getBranch() != null && Objects.equals(actor.getBranch().getId(), branch.getId())) return;
        throw ApiException.forbidden("Bạn không có quyền quản lý nhân sự tại chi nhánh này");
    }

    private void requireStoreAccess(User actor, Store store) {
        if (actor.getRole() == UserRole.ROLE_ADMIN) return;
        if (actor.getRole() == UserRole.ROLE_STORE_ADMIN && store.getStoreAdmin() != null && Objects.equals(store.getStoreAdmin().getId(), actor.getId())) return;
        if (!Objects.equals(storeFor(actor).getId(), store.getId())) throw ApiException.forbidden("Dữ liệu không thuộc cửa hàng hiện tại");
    }

    private void requireSelfBranch(User actor, Branch branch) {
        if (actor.getBranch() == null || !Objects.equals(actor.getBranch().getId(), branch.getId())) {
            throw ApiException.forbidden("Chỉ được chấm công tại chi nhánh được phân công");
        }
    }

    private void requireEmployeeInBranch(User employee, Branch branch) {
        boolean assignedBranch = employee.getBranch() != null && Objects.equals(employee.getBranch().getId(), branch.getId());
        boolean assignedStore = employee.getBranch() == null && employee.getStore() != null
                && Objects.equals(employee.getStore().getId(), branch.getStore().getId());
        if (!assignedBranch && !assignedStore) {
            throw ApiException.badRequest("Nhân viên chưa được phân công vào chi nhánh này");
        }
    }

    private boolean isEmployeeSelfOnly(User actor) { return actor.getRole() == UserRole.ROLE_BRANCH_CASHIER; }
    private boolean canManagePayroll(User actor) { return actor.getRole() == UserRole.ROLE_ADMIN || actor.getRole() == UserRole.ROLE_STORE_ADMIN || actor.getRole() == UserRole.ROLE_STORE_MANAGER; }

    private User requiredEmployee(Long id) { return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên")); }
    private Branch requiredBranch(Long id) { return branchRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy chi nhánh")); }
    private Store requiredStore(Long id) { return storeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng")); }

    private Store storeFor(User user) {
        if (user.getStore() != null) return user.getStore();
        if (user.getBranch() != null && user.getBranch().getStore() != null) return user.getBranch().getStore();
        Store owned = storeRepository.findByStoreAdminId(user.getId());
        if (owned != null) return owned;
        throw ApiException.forbidden("Tài khoản chưa thuộc cửa hàng hợp lệ");
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now().plusDays(31) : to;
        LocalDate start = from == null ? LocalDate.now().minusDays(31) : from;
        if (start.isAfter(end) || start.plusDays(MAX_DAYS - 1L).isBefore(end)) throw ApiException.badRequest("Khoảng thời gian không hợp lệ hoặc vượt quá 366 ngày");
        return new Range(start, end);
    }

    private BigDecimal nonNegative(BigDecimal value, String field) {
        BigDecimal resolved = value == null ? BigDecimal.ZERO : value;
        if (resolved.signum() < 0) throw ApiException.badRequest(field + " không được âm");
        return resolved;
    }

    private int calculateWorkedMinutes(Long employeeId, LocalDate from, LocalDate to) {
        long total = attendanceRepository
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(employeeId, from, to).stream()
                .filter(record -> record.getCheckIn() != null && record.getCheckOut() != null)
                .filter(record -> record.getStatus() == AttendanceStatus.PRESENT || record.getStatus() == AttendanceStatus.LATE)
                .mapToLong(record -> Math.max(0L, Duration.between(record.getCheckIn(), record.getCheckOut()).toMinutes()))
                .sum();
        if (total > Integer.MAX_VALUE) throw ApiException.badRequest("Tổng thời gian chấm công vượt giới hạn");
        return (int) total;
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private WorkScheduleDTO scheduleDto(WorkSchedule value) {
        WorkScheduleDTO dto = new WorkScheduleDTO();
        dto.setId(value.getId()); dto.setEmployeeId(value.getEmployee().getId()); dto.setEmployeeName(value.getEmployee().getFullName());
        dto.setBranchId(value.getBranch().getId()); dto.setBranchName(value.getBranch().getName()); dto.setWorkDate(value.getWorkDate());
        dto.setStartTime(value.getStartTime()); dto.setEndTime(value.getEndTime()); dto.setStatus(value.getStatus()); dto.setNote(value.getNote());
        return dto;
    }

    private AttendanceDTO attendanceDto(Attendance value) {
        AttendanceDTO dto = new AttendanceDTO();
        dto.setId(value.getId()); dto.setEmployeeId(value.getEmployee().getId()); dto.setEmployeeName(value.getEmployee().getFullName());
        dto.setBranchId(value.getBranch().getId()); dto.setBranchName(value.getBranch().getName()); dto.setWorkDate(value.getWorkDate());
        dto.setCheckIn(value.getCheckIn()); dto.setCheckOut(value.getCheckOut()); dto.setStatus(value.getStatus()); dto.setNote(value.getNote());
        return dto;
    }

    private PayrollDTO payrollDto(Payroll value) {
        PayrollDTO dto = new PayrollDTO();
        dto.setId(value.getId()); dto.setEmployeeId(value.getEmployee().getId()); dto.setEmployeeName(value.getEmployee().getFullName());
        dto.setStoreId(value.getStore().getId()); dto.setStoreName(value.getStore().getBrand()); dto.setPeriodStart(value.getPeriodStart()); dto.setPeriodEnd(value.getPeriodEnd());
        dto.setBaseSalary(value.getBaseSalary()); dto.setBonus(value.getBonus()); dto.setDeduction(value.getDeduction()); dto.setNetSalary(value.getNetSalary());
        dto.setHourlyRate(value.getHourlyRate()); dto.setWorkedMinutes(value.getWorkedMinutes());
        dto.setStatus(value.getStatus()); dto.setPaidAt(value.getPaidAt()); dto.setNote(value.getNote());
        return dto;
    }

    private User currentUser() {
        try { return userService.getCurrentUser(); }
        catch (UserException exception) { throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại"); }
    }

    private record WorkforceScope(Long storeId, Long branchId) {}
    private record Range(LocalDate from, LocalDate to) {}
}
