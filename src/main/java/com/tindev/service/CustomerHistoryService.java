package com.tindev.service;

import com.tindev.payload.dto.CustomerHistoryDTO;

public interface CustomerHistoryService {
    CustomerHistoryDTO getCustomerHistory(Long customerId, int page, int pageSize);
}
