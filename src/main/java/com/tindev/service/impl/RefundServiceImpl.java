package com.tindev.service.impl;

import com.tindev.domain.OrderStatus;
import com.tindev.domain.InventoryMovementType;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.ProductMapper;
import com.tindev.mapper.RefundMapper;
import com.tindev.modal.Order;
import com.tindev.modal.OrderItem;
import com.tindev.modal.Refund;
import com.tindev.modal.RefundItem;
import com.tindev.modal.ShiftReport;
import com.tindev.modal.User;
import com.tindev.payload.dto.RefundDTO;
import com.tindev.payload.dto.RefundItemDTO;
import com.tindev.repository.OrderRepository;
import com.tindev.repository.RefundItemRepository;
import com.tindev.repository.RefundRepository;
import com.tindev.repository.ShiftReportRepository;
import com.tindev.service.RefundService;
import com.tindev.service.AuditLogService;
import com.tindev.service.InventoryService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final UserService userService;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final InventoryService inventoryService;
    private final ShiftReportRepository shiftReportRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public RefundDTO createRefund(RefundDTO request) throws Exception {
        if (request.getOrderId() == null || request.getReason() == null || request.getReason().isBlank()) {
            throw ApiException.badRequest("Refund cần mã đơn hàng và lý do");
        }
        User cashier = userService.getCurrentUser();
        ShiftReport shift = shiftReportRepository.findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(cashier)
                .orElseThrow(() -> ApiException.conflict("Bạn cần mở ca trước khi hoàn tiền"));
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        if (cashier.getBranch() == null || !Objects.equals(order.getBranch().getId(), cashier.getBranch().getId())) {
            throw ApiException.forbidden("Chỉ có thể hoàn tiền cho đơn tại chi nhánh hiện tại");
        }
        if (order.getStatus() == OrderStatus.REFUNDED) {
            throw ApiException.conflict("Đơn hàng đã được hoàn tiền toàn bộ");
        }

        Map<Long, OrderItem> orderItems = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            orderItems.put(item.getProduct().getId(), item);
        }
        Map<Long, Integer> previouslyRefunded = refundedQuantities(order.getId());
        List<RefundItemDTO> selectedItems = request.getItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            selectedItems = order.getItems().stream().map(item -> RefundItemDTO.builder()
                    .productId(item.getProduct().getId())
                    .quantity(item.getQuantity() - previouslyRefunded.getOrDefault(item.getProduct().getId(), 0)).build())
                    .filter(item -> item.getQuantity() > 0).toList();
        }

        Refund refund = Refund.builder().order(order).cashier(cashier).branch(order.getBranch())
                .shiftReport(shift).paymentType(order.getPaymentType()).reason(request.getReason().trim()).build();
        List<RefundItem> refundItems = new ArrayList<>();
        Map<Long, Integer> selectedQuantities = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (RefundItemDTO selected : selectedItems) {
            if (selected.getProductId() == null || selected.getQuantity() == null || selected.getQuantity() <= 0) {
                throw ApiException.badRequest("Mỗi dòng hoàn tiền cần sản phẩm và số lượng lớn hơn 0");
            }
            OrderItem original = orderItems.get(selected.getProductId());
            if (original == null) {
                throw ApiException.badRequest("Sản phẩm hoàn tiền không có trong đơn hàng");
            }
            int remaining = original.getQuantity() - previouslyRefunded.getOrDefault(selected.getProductId(), 0)
                    - selectedQuantities.getOrDefault(selected.getProductId(), 0);
            if (selected.getQuantity() > remaining) {
                throw ApiException.conflict("Số lượng hoàn tiền vượt quá số lượng đã bán");
            }
            selectedQuantities.merge(selected.getProductId(), selected.getQuantity(), Integer::sum);
            BigDecimal unitPrice = original.getPrice().divide(BigDecimal.valueOf(original.getQuantity()));
            BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(selected.getQuantity()));
            BigDecimal originalCost = original.getCostAmount() == null ? BigDecimal.ZERO : original.getCostAmount();
            BigDecimal unitCost = originalCost.divide(BigDecimal.valueOf(original.getQuantity()), 2,
                    java.math.RoundingMode.HALF_UP);
            BigDecimal lineCost = unitCost.multiply(BigDecimal.valueOf(selected.getQuantity()));
            inventoryService.adjustInventoryForTransaction(selected.getProductId(), order.getBranch().getId(), selected.getQuantity(),
                    InventoryMovementType.REFUND, "Hoàn tiền đơn #" + order.getId(), cashier);
            refundItems.add(RefundItem.builder().refund(refund).product(original.getProduct())
                    .quantity(selected.getQuantity()).amount(lineAmount).costAmount(lineCost).build());
            total = total.add(lineAmount);
        }
        if (refundItems.isEmpty()) {
            throw ApiException.conflict("Không còn sản phẩm đủ điều kiện để hoàn tiền");
        }
        refund.setAmount(total);
        refund.setItems(refundItems);
        Refund saved = refundRepository.save(refund);
        updateOrderStatus(order, totalRefunded(order.getId()));
        auditLogService.record(order.getBranch().getStore(), cashier, "REFUND_CREATED", "REFUND", saved.getId(),
                "Order=" + order.getId() + ", amount=" + saved.getAmount());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getAllRefund() {
        User current = currentUser();
        return refundRepository.findAll().stream().filter(refund -> hasRefundAccess(current, refund)).map(this::toDto).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByCashier(Long cashierId) {
        User current = currentUser();
        return refundRepository.findByCashierId(cashierId).stream().filter(refund -> hasRefundAccess(current, refund)).map(this::toDto).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByShiftReport(Long shiftReportId) {
        User current = currentUser();
        return refundRepository.findByShiftReportId(shiftReportId).stream().filter(refund -> hasRefundAccess(current, refund)).map(this::toDto).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByCashierAndDateRange(Long cashierId, LocalDateTime startDate, LocalDateTime endDate) {
        User current = currentUser();
        return refundRepository.findByCashierIdAndCreatedAtBetween(cashierId, startDate, endDate).stream()
                .filter(refund -> hasRefundAccess(current, refund)).map(this::toDto).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByBranch(Long branchId) {
        User current = currentUser();
        return refundRepository.findByBranchId(branchId).stream().filter(refund -> hasRefundAccess(current, refund)).map(this::toDto).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public RefundDTO getRefundById(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giao dịch hoàn tiền"));
        if (!hasRefundAccess(currentUser(), refund)) {
            throw ApiException.forbidden("Bạn không có quyền xem giao dịch hoàn tiền này");
        }
        return toDto(refund);
    }

    private Map<Long, Integer> refundedQuantities(Long orderId) {
        Map<Long, Integer> result = new HashMap<>();
        for (RefundItem item : refundItemRepository.findByRefundOrderId(orderId)) {
            result.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }
        return result;
    }

    private BigDecimal totalRefunded(Long orderId) {
        return refundRepository.findByOrderId(orderId).stream()
                .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void updateOrderStatus(Order order, BigDecimal refundedAmount) {
        order.setStatus(refundedAmount.compareTo(order.getTotalAmount()) >= 0 ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED);
        orderRepository.save(order);
    }

    private RefundDTO toDto(Refund refund) {
        RefundDTO dto = RefundMapper.toDTO(refund);
        dto.setPaymentType(refund.getPaymentType());
        dto.setItems(refund.getItems().stream().map(item -> RefundItemDTO.builder().id(item.getId())
                .productId(item.getProduct().getId()).product(ProductMapper.toDTO(item.getProduct()))
                .quantity(item.getQuantity()).amount(item.getAmount()).build()).toList());
        return dto;
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại");
        }
    }

    private boolean hasRefundAccess(User user, Refund refund) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }
        if (refund.getBranch() == null || refund.getBranch().getStore() == null) {
            return false;
        }
        Long storeId = user.getStore() != null ? user.getStore().getId()
                : user.getBranch() != null && user.getBranch().getStore() != null ? user.getBranch().getStore().getId() : null;
        boolean ownedStore = user.getRole() == UserRole.ROLE_STORE_ADMIN
                && refund.getBranch().getStore().getStoreAdmin() != null
                && Objects.equals(refund.getBranch().getStore().getStoreAdmin().getId(), user.getId());
        if (!ownedStore && !Objects.equals(storeId, refund.getBranch().getStore().getId())) {
            return false;
        }
        boolean branchScoped = user.getRole() == UserRole.ROLE_BRANCH_CASHIER
                || user.getRole() == UserRole.ROLE_BRANCH_MANAGER;
        return !branchScoped || (user.getBranch() != null && Objects.equals(user.getBranch().getId(), refund.getBranch().getId()));
    }
}
