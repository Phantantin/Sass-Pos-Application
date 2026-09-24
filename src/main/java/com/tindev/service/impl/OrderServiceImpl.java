package com.tindev.service.impl;

import com.tindev.domain.OrderStatus;
import com.tindev.domain.PaymentType;
import com.tindev.domain.InventoryMovementType;
import com.tindev.domain.StoreStatus;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.OrderMapper;
import com.tindev.modal.Branch;
import com.tindev.modal.Customer;
import com.tindev.modal.Order;
import com.tindev.modal.OrderItem;
import com.tindev.modal.Product;
import com.tindev.modal.ShiftReport;
import com.tindev.modal.User;
import com.tindev.payload.dto.OrderDTO;
import com.tindev.payload.dto.OrderItemDTO;
import com.tindev.repository.CustomerRepository;
import com.tindev.repository.OrderRepository;
import com.tindev.repository.ProductRepository;
import com.tindev.repository.ShiftReportRepository;
import com.tindev.repository.BranchRepository;
import com.tindev.service.OrderService;
import com.tindev.service.AuditLogService;
import com.tindev.service.InventoryService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final UserService userService;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final BranchRepository branchRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public OrderDTO createOrder(OrderDTO orderDTO, String idempotencyKey) throws Exception {
        if (orderDTO == null) {
            throw ApiException.badRequest("Dữ liệu đơn hàng không hợp lệ");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("Thiếu Idempotency-Key cho đơn hàng");
        }
        if (orderDTO.getItems() == null || orderDTO.getItems().isEmpty()) {
            throw ApiException.badRequest("Đơn hàng cần có ít nhất một sản phẩm");
        }
        if (orderDTO.getPaymentType() == null) {
            throw ApiException.badRequest("Vui lòng chọn phương thức thanh toán");
        }

        User cashier = userService.getCurrentUser();
        Branch branch = cashier.getBranch();
        if (branch == null || branch.getStore() == null) {
            throw ApiException.forbidden("Tài khoản chưa được phân công chi nhánh hợp lệ");
        }
        if (branch.getStore().getStatus() != StoreStatus.ACTIVE) {
            throw ApiException.forbidden("Cửa hàng chưa được kích hoạt để bán hàng");
        }
        Order existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingOrder != null) {
            if (!Objects.equals(existingOrder.getBranch().getId(), branch.getId())) {
                throw ApiException.conflict("Idempotency-Key đã được dùng cho đơn hàng khác");
            }
            return OrderMapper.toDTO(existingOrder);
        }
        ShiftReport activeShift = shiftReportRepository
                .findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(cashier)
                .orElseThrow(() -> ApiException.conflict("Bạn cần mở ca trước khi tạo đơn hàng"));

        Customer customer = resolveCustomer(orderDTO.getCustomerId(), branch.getStore().getId());
        Order order = Order.builder()
                .branch(branch)
                .cashier(cashier)
                .customer(customer)
                .paymentType(orderDTO.getPaymentType())
                .status(OrderStatus.COMPLETED)
                .idempotencyKey(idempotencyKey)
                .shiftReport(activeShift)
                .build();

        Map<Long, Integer> requestedQuantities = new LinkedHashMap<>();
        for (OrderItemDTO itemDTO : orderDTO.getItems()) {
            validateItem(itemDTO);
            requestedQuantities.merge(itemDTO.getProductId(), itemDTO.getQuantity(), Math::addExact);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> requestedItem : requestedQuantities.entrySet()) {
            Product product = productRepository.findById(requestedItem.getKey())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
            if (!Objects.equals(product.getStore().getId(), branch.getStore().getId())) {
                throw ApiException.forbidden("Sản phẩm không thuộc cửa hàng hiện tại");
            }
            inventoryService.adjustInventoryForTransaction(product.getId(), branch.getId(), -requestedItem.getValue(),
                    InventoryMovementType.SALE, "Bán hàng", cashier);

            BigDecimal lineTotal = product.getSellingPrice().multiply(BigDecimal.valueOf(requestedItem.getValue()));
            BigDecimal unitCost = product.getCostPrice() == null ? BigDecimal.ZERO : product.getCostPrice();
            BigDecimal lineCost = unitCost.multiply(BigDecimal.valueOf(requestedItem.getValue()));
            items.add(OrderItem.builder().order(order).product(product).quantity(requestedItem.getValue())
                    .price(lineTotal).costAmount(lineCost).build());
            total = total.add(lineTotal);
        }
        order.setTotalAmount(total);
        order.setItems(items);
        Order saved = orderRepository.save(order);
        auditLogService.record(branch.getStore(), cashier, "ORDER_CREATED", "ORDER", saved.getId(),
                "Total=" + saved.getTotalAmount() + ", payment=" + saved.getPaymentType());
        return OrderMapper.toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Long id) {
        Order order = orderRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        requireOrderAccess(currentUser(), order);
        return OrderMapper.toDTO(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByBranch(Long branchId, Long customerId, Long cashierId,
                                             PaymentType paymentType, OrderStatus status) {
        requireBranchAccess(currentUser(), findBranch(branchId));
        return orderRepository.findByBranchId(branchId).stream()
                .filter(order -> customerId == null || (order.getCustomer() != null && customerId.equals(order.getCustomer().getId())))
                .filter(order -> cashierId == null || cashierId.equals(order.getCashier().getId()))
                .filter(order -> paymentType == null || paymentType == order.getPaymentType())
                .filter(order -> status == null || status == order.getStatus())
                .map(OrderMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrderByCashierId(Long cashierId) {
        User current = currentUser();
        return orderRepository.findByCashierId(cashierId).stream()
                .filter(order -> hasOrderAccess(current, order)).map(OrderMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getTodayOrdersByBranch(Long branchId) {
        requireBranchAccess(currentUser(), findBranch(branchId));
        LocalDate today = LocalDate.now();
        return orderRepository.findByBranchIdAndCreatedAtBetween(branchId, today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream().map(OrderMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByCustomerId(Long customerId) {
        User current = currentUser();
        return orderRepository.findByCustomerId(customerId).stream()
                .filter(order -> hasOrderAccess(current, order)).map(OrderMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getTop5RecentOrdersByBranchId(Long branchId) {
        requireBranchAccess(currentUser(), findBranch(branchId));
        return orderRepository.findTo5ByBranchIdOrderByCreatedAtDesc(branchId).stream().map(OrderMapper::toDTO).toList();
    }

    private Customer resolveCustomer(Long customerId, Long storeId) {
        if (customerId == null) {
            return null;
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        if (!Objects.equals(customer.getStore().getId(), storeId)) {
            throw ApiException.forbidden("Khách hàng không thuộc cửa hàng hiện tại");
        }
        return customer;
    }

    private void validateItem(OrderItemDTO item) {
        if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
            throw ApiException.badRequest("Mỗi sản phẩm cần productId và số lượng lớn hơn 0");
        }
    }

    private Branch findBranch(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi nhánh"));
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại");
        }
    }

    private void requireOrderAccess(User user, Order order) {
        if (!hasOrderAccess(user, order)) {
            throw ApiException.forbidden("Bạn không có quyền xem đơn hàng này");
        }
    }

    private boolean hasOrderAccess(User user, Order order) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }
        try {
            requireBranchAccess(user, order.getBranch());
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }

    private void requireBranchAccess(User user, Branch branch) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (branch == null || branch.getStore() == null) {
            throw ApiException.forbidden("Dữ liệu không thuộc cửa hàng hiện tại");
        }
        boolean ownedStore = user.getRole() == UserRole.ROLE_STORE_ADMIN
                && branch.getStore().getStoreAdmin() != null
                && Objects.equals(branch.getStore().getStoreAdmin().getId(), user.getId());
        if (!ownedStore && !Objects.equals(storeIdFor(user), branch.getStore().getId())) {
            throw ApiException.forbidden("Dữ liệu không thuộc cửa hàng hiện tại");
        }
        boolean branchScoped = user.getRole() == UserRole.ROLE_BRANCH_CASHIER
                || user.getRole() == UserRole.ROLE_BRANCH_MANAGER;
        if (branchScoped && (user.getBranch() == null || !Objects.equals(user.getBranch().getId(), branch.getId()))) {
            throw ApiException.forbidden("Bạn chỉ có thể xem dữ liệu của chi nhánh được phân công");
        }
    }

    private Long storeIdFor(User user) {
        if (user.getStore() != null) {
            return user.getStore().getId();
        }
        if (user.getBranch() != null && user.getBranch().getStore() != null) {
            return user.getBranch().getStore().getId();
        }
        return null;
    }
}
