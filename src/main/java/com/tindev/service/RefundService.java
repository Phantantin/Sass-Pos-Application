package com.tindev.service;

import com.tindev.payload.dto.RefundDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface RefundService {
    RefundDTO createRefund(RefundDTO refund) throws Exception;
    List<RefundDTO> getAllRefund();
    List<RefundDTO> getRefundByCashier(Long cashierId);
    List<RefundDTO> getRefundByShiftReport(Long shiftReportId);
    List<RefundDTO> getRefundByCashierAndDateRange(Long cashierId, LocalDateTime startDate, LocalDateTime endDate);
    List<RefundDTO> getRefundByBranch(Long branchId);
    RefundDTO getRefundById(Long refundId);
}
