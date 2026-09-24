package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.UserMapper;
import com.tindev.modal.Branch;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.UserDto;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.UserRepository;
import com.tindev.service.EmployeeService;
import com.tindev.service.AuditLogService;
import com.tindev.service.SubscriptionService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public UserDto createStoreEmployee(UserDto employee, Long storeId) {
        Store store = findStore(storeId);
        User currentUser = currentUser();
        requireStoreEmployeeManagement(currentUser, store);
        validateNewEmployee(employee);
        requireAssignableRole(currentUser, employee.getRole());
        subscriptionService.assertCanCreateEmployee(store);

        Branch branch = resolveBranchForStoreEmployee(employee, store);
        ensureEmailAvailable(normalizeEmail(employee.getEmail()), null);
        ensureBranchManagerAvailable(branch, employee.getRole(), null);

        User savedEmployee = userRepository.save(newEmployee(employee, store, branch));
        assignBranchManager(branch, savedEmployee);
        auditLogService.record(store, currentUser, "EMPLOYEE_CREATED", "USER", savedEmployee.getId(),
                "Role=" + savedEmployee.getRole());
        return UserMapper.toDTO(savedEmployee);
    }

    @Override
    @Transactional
    public UserDto createBranchEmployee(UserDto employee, Long branchId) {
        Branch branch = findBranch(branchId);
        User currentUser = currentUser();
        requireBranchEmployeeManagement(currentUser, branch);
        validateNewEmployee(employee);
        requireAssignableRole(currentUser, employee.getRole());
        subscriptionService.assertCanCreateEmployee(branch.getStore());
        if (!isBranchRole(employee.getRole())) {
            throw ApiException.badRequest("Endpoint chi nhánh chỉ hỗ trợ vai trò quản lý chi nhánh hoặc thu ngân");
        }
        if (employee.getBranchId() != null && !Objects.equals(employee.getBranchId(), branchId)) {
            throw ApiException.badRequest("branchId trong dữ liệu phải khớp với branchId trên URL");
        }

        ensureEmailAvailable(normalizeEmail(employee.getEmail()), null);
        ensureBranchManagerAvailable(branch, employee.getRole(), null);
        User savedEmployee = userRepository.save(newEmployee(employee, branch.getStore(), branch));
        assignBranchManager(branch, savedEmployee);
        auditLogService.record(branch.getStore(), currentUser, "EMPLOYEE_CREATED", "USER", savedEmployee.getId(),
                "Role=" + savedEmployee.getRole());
        return UserMapper.toDTO(savedEmployee);
    }

    @Override
    @Transactional
    public UserDto updateEmployee(Long employeeId, UserDto employeeDetails) {
        User employee = findEmployee(employeeId);
        Store employeeStore = storeFor(employee);
        User currentUser = currentUser();
        requireEmployeeManagement(currentUser, employee, employeeStore);
        validateEmployeeDetails(employeeDetails);
        requireAssignableRole(currentUser, employeeDetails.getRole());

        if (employeeDetails.getStoreId() != null && !Objects.equals(employeeDetails.getStoreId(), employeeStore.getId())) {
            throw ApiException.badRequest("Không thể chuyển nhân viên sang cửa hàng khác bằng endpoint này");
        }

        Branch oldBranch = employee.getBranch();
        UserRole previousRole = employee.getRole();
        Branch newBranch = resolveBranchForUpdate(employeeDetails, employeeStore, oldBranch);
        ensureEmailAvailable(normalizeEmail(employeeDetails.getEmail()), employee.getId());
        ensureBranchManagerAvailable(newBranch, employeeDetails.getRole(), employee.getId());

        clearOldBranchManager(employee, oldBranch, newBranch, employeeDetails.getRole());
        employee.setFullName(requiredText(employeeDetails.getFullName(), "Họ tên"));
        employee.setEmail(normalizeEmail(employeeDetails.getEmail()));
        employee.setPhone(trimToNull(employeeDetails.getPhone()));
        employee.setRole(employeeDetails.getRole());
        employee.setStore(employeeStore);
        employee.setBranch(newBranch);
        updatePasswordIfPresent(employee, employeeDetails.getPassword());
        employee.setUpdatedAt(LocalDateTime.now());

        User savedEmployee = userRepository.save(employee);
        assignBranchManager(newBranch, savedEmployee);
        auditLogService.record(employeeStore, currentUser,
                previousRole == savedEmployee.getRole() ? "EMPLOYEE_UPDATED" : "EMPLOYEE_ROLE_CHANGED",
                "USER", savedEmployee.getId(), "Role=" + savedEmployee.getRole());
        return UserMapper.toDTO(savedEmployee);
    }

    @Override
    @Transactional
    public void deleteEmployee(Long employeeId) {
        User employee = findEmployee(employeeId);
        Store employeeStore = storeFor(employee);
        User currentUser = currentUser();
        requireEmployeeManagement(currentUser, employee, employeeStore);

        clearOldBranchManager(employee, employee.getBranch(), null, null);
        auditLogService.record(employeeStore, currentUser, "EMPLOYEE_DELETED", "USER", employee.getId(),
                "Role=" + employee.getRole());
        userRepository.delete(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findStoreEmployees(Long storeId, UserRole role) {
        Store store = findStore(storeId);
        requireStoreEmployeeRead(currentUser(), store);
        return userRepository.findByStore(store).stream()
                .filter(user -> role == null || user.getRole() == role)
                .map(UserMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findBranchEmployees(Long branchId, UserRole role) {
        Branch branch = findBranch(branchId);
        requireBranchEmployeeRead(currentUser(), branch);
        return userRepository.findByBranchId(branchId).stream()
                .filter(user -> role == null || user.getRole() == role)
                .map(UserMapper::toDTO)
                .toList();
    }

    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng"));
    }

    private Branch findBranch(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi nhánh"));
    }

    private User findEmployee(Long employeeId) {
        return userRepository.findById(employeeId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
    }

    private void requireStoreEmployeeManagement(User currentUser, Store store) {
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        requireSameStore(currentUser, store);
        if (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN) {
            return;
        }
        throw ApiException.forbidden("Bạn không có quyền quản lý nhân viên của cửa hàng này");
    }

    private void requireStoreEmployeeRead(User currentUser, Store store) {
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        requireSameStore(currentUser, store);
        if (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN
                || currentUser.getRole() == UserRole.ROLE_STORE_MANAGER) {
            return;
        }
        throw ApiException.forbidden("Bạn không có quyền xem nhân sự của cửa hàng này");
    }

    private void requireBranchEmployeeManagement(User currentUser, Branch branch) {
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        requireSameStore(currentUser, branch.getStore());
        if (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN) {
            return;
        }
        if (currentUser.getRole() == UserRole.ROLE_BRANCH_MANAGER
                && currentUser.getBranch() != null
                && Objects.equals(currentUser.getBranch().getId(), branch.getId())) {
            return;
        }
        throw ApiException.forbidden("Bạn không có quyền quản lý nhân viên của chi nhánh này");
    }

    private void requireBranchEmployeeRead(User currentUser, Branch branch) {
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        requireSameStore(currentUser, branch.getStore());
        if (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN
                || currentUser.getRole() == UserRole.ROLE_STORE_MANAGER) {
            return;
        }
        if (currentUser.getRole() == UserRole.ROLE_BRANCH_MANAGER
                && currentUser.getBranch() != null
                && Objects.equals(currentUser.getBranch().getId(), branch.getId())) {
            return;
        }
        throw ApiException.forbidden("Bạn không có quyền xem nhân sự của chi nhánh này");
    }

    private void requireEmployeeManagement(User currentUser, User employee, Store employeeStore) {
        if (!isManagedEmployeeRole(employee.getRole())) {
            throw ApiException.forbidden("Không thể quản lý tài khoản quản trị bằng endpoint nhân viên");
        }
        if (currentUser.getRole() != UserRole.ROLE_ADMIN) {
            requireSameStore(currentUser, employeeStore);
            if (currentUser.getRole() == UserRole.ROLE_BRANCH_MANAGER
                    && (employee.getBranch() == null || currentUser.getBranch() == null
                    || !Objects.equals(currentUser.getBranch().getId(), employee.getBranch().getId()))) {
                throw ApiException.forbidden("Bạn chỉ có thể quản lý nhân viên trong chi nhánh được phân công");
            }
            if (currentUser.getRole() != UserRole.ROLE_STORE_ADMIN
                    && currentUser.getRole() != UserRole.ROLE_BRANCH_MANAGER) {
                throw ApiException.forbidden("Bạn không có quyền quản lý nhân viên");
            }
        }
        if (roleRank(currentUser.getRole()) <= roleRank(employee.getRole())) {
            throw ApiException.forbidden("Không thể quản lý nhân viên có vai trò ngang hoặc cao hơn");
        }
    }

    private void requireSameStore(User currentUser, Store targetStore) {
        if (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN && targetStore != null
                && targetStore.getStoreAdmin() != null
                && Objects.equals(targetStore.getStoreAdmin().getId(), currentUser.getId())) {
            return;
        }
        Store currentStore = storeFor(currentUser);
        if (!sameStore(currentStore, targetStore)) {
            throw ApiException.forbidden("Dữ liệu nhân viên không thuộc cửa hàng hiện tại");
        }
    }

    private Store storeFor(User user) {
        if (user.getStore() != null) {
            return user.getStore();
        }
        if (user.getBranch() != null && user.getBranch().getStore() != null) {
            return user.getBranch().getStore();
        }
        Store ownedStore = storeRepository.findByStoreAdminId(user.getId());
        if (ownedStore != null) {
            return ownedStore;
        }
        throw ApiException.forbidden("Tài khoản chưa thuộc cửa hàng hợp lệ");
    }

    private Branch resolveBranchForStoreEmployee(UserDto employee, Store store) {
        if (!isBranchRole(employee.getRole())) {
            if (employee.getBranchId() != null) {
                throw ApiException.badRequest("Vai trò cấp cửa hàng không được gán chi nhánh");
            }
            return null;
        }
        if (employee.getBranchId() == null) {
            throw ApiException.badRequest("Cần chọn chi nhánh cho vai trò cấp chi nhánh");
        }
        Branch branch = findBranch(employee.getBranchId());
        ensureBranchInStore(branch, store);
        return branch;
    }

    private Branch resolveBranchForUpdate(UserDto employee, Store store, Branch existingBranch) {
        if (!isBranchRole(employee.getRole())) {
            if (employee.getBranchId() != null) {
                throw ApiException.badRequest("Vai trò cấp cửa hàng không được gán chi nhánh");
            }
            return null;
        }
        if (employee.getBranchId() == null) {
            if (existingBranch == null) {
                throw ApiException.badRequest("Cần chọn chi nhánh cho vai trò cấp chi nhánh");
            }
            ensureBranchInStore(existingBranch, store);
            return existingBranch;
        }
        Branch branch = findBranch(employee.getBranchId());
        ensureBranchInStore(branch, store);
        return branch;
    }

    private void ensureBranchInStore(Branch branch, Store store) {
        if (!sameStore(branch.getStore(), store)) {
            throw ApiException.forbidden("Chi nhánh không thuộc cửa hàng hiện tại");
        }
    }

    private void ensureBranchManagerAvailable(Branch branch, UserRole role, Long employeeId) {
        if (role != UserRole.ROLE_BRANCH_MANAGER || branch == null) {
            return;
        }
        User currentManager = branch.getManager();
        if (currentManager != null && !Objects.equals(currentManager.getId(), employeeId)) {
            throw ApiException.conflict("Chi nhánh đã có quản lý được phân công");
        }
    }

    private void assignBranchManager(Branch branch, User employee) {
        if (employee.getRole() != UserRole.ROLE_BRANCH_MANAGER || branch == null) {
            return;
        }
        branch.setManager(employee);
        branchRepository.save(branch);
    }

    private void clearOldBranchManager(User employee, Branch oldBranch, Branch newBranch, UserRole newRole) {
        if (oldBranch == null || oldBranch.getManager() == null
                || !Objects.equals(oldBranch.getManager().getId(), employee.getId())) {
            return;
        }
        boolean remainsManagerOfSameBranch = newRole == UserRole.ROLE_BRANCH_MANAGER
                && newBranch != null && Objects.equals(oldBranch.getId(), newBranch.getId());
        if (!remainsManagerOfSameBranch) {
            oldBranch.setManager(null);
            branchRepository.save(oldBranch);
        }
    }

    private User newEmployee(UserDto employee, Store store, Branch branch) {
        User user = new User();
        user.setFullName(requiredText(employee.getFullName(), "Họ tên"));
        user.setEmail(normalizeEmail(employee.getEmail()));
        user.setPhone(trimToNull(employee.getPhone()));
        user.setRole(employee.getRole());
        user.setPassword(passwordEncoder.encode(requiredPassword(employee.getPassword())));
        user.setStore(store);
        user.setBranch(branch);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    private void validateNewEmployee(UserDto employee) {
        validateEmployeeDetails(employee);
        requiredPassword(employee.getPassword());
    }

    private void validateEmployeeDetails(UserDto employee) {
        requiredText(employee.getFullName(), "Họ tên");
        normalizeEmail(employee.getEmail());
        if (employee.getRole() == null) {
            throw ApiException.badRequest("Vai trò là bắt buộc");
        }
        if (employee.getPassword() != null && (employee.getPassword().isBlank()
                || employee.getPassword().length() < 8 || employee.getPassword().length() > 100)) {
            throw ApiException.badRequest("Mật khẩu phải có từ 8 đến 100 ký tự");
        }
    }

    private void requireAssignableRole(User currentUser, UserRole requestedRole) {
        if (!isManagedEmployeeRole(requestedRole)) {
            throw ApiException.badRequest("Không thể gán vai trò quản trị qua endpoint nhân viên");
        }
        if (roleRank(currentUser.getRole()) <= roleRank(requestedRole)) {
            throw ApiException.forbidden("Không thể gán vai trò ngang hoặc cao hơn vai trò hiện tại");
        }
    }

    private boolean isManagedEmployeeRole(UserRole role) {
        return role == UserRole.ROLE_STORE_ADMIN
                || role == UserRole.ROLE_STORE_MANAGER
                || role == UserRole.ROLE_BRANCH_MANAGER
                || role == UserRole.ROLE_BRANCH_CASHIER;
    }

    private boolean isBranchRole(UserRole role) {
        return role == UserRole.ROLE_BRANCH_MANAGER || role == UserRole.ROLE_BRANCH_CASHIER;
    }

    private int roleRank(UserRole role) {
        if (role == null) {
            return -1;
        }
        return switch (role) {
            case ROLE_ADMIN -> 4;
            case ROLE_STORE_ADMIN -> 3;
            case ROLE_STORE_MANAGER -> 2;
            case ROLE_BRANCH_MANAGER -> 1;
            case ROLE_BRANCH_CASHIER -> 0;
        };
    }

    private void ensureEmailAvailable(String email, Long currentEmployeeId) {
        User existing = userRepository.findByEmail(email);
        if (existing != null && !Objects.equals(existing.getId(), currentEmployeeId)) {
            throw ApiException.conflict("Email đã được sử dụng");
        }
    }

    private void updatePasswordIfPresent(User employee, String password) {
        if (password == null) {
            return;
        }
        employee.setPassword(passwordEncoder.encode(requiredPassword(password)));
    }

    private String requiredPassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 100) {
            throw ApiException.badRequest("Mật khẩu phải có từ 8 đến 100 ký tự");
        }
        return password;
    }

    private String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(label + " là bắt buộc");
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        return requiredText(email, "Email").toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean sameStore(Store left, Store right) {
        return left != null && right != null && Objects.equals(left.getId(), right.getId());
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại");
        }
    }
}
