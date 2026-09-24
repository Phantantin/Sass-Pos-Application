package com.tindev.controller;

import com.tindev.configuration.JwtProvider;
import com.tindev.configuration.SecurityConfig;
import com.tindev.exceptions.RestAccessDeniedHandler;
import com.tindev.exceptions.RestAuthenticationEntryPoint;
import com.tindev.payload.dto.AuditLogDTO;
import com.tindev.payload.dto.AuditLogFilter;
import com.tindev.payload.dto.AuditLogPageDTO;
import com.tindev.payload.dto.CustomerHistoryDTO;
import com.tindev.service.AuditLogService;
import com.tindev.service.CustomerHistoryService;
import com.tindev.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that controller-level authorization runs before scoped services and
 * that a tenant decision returned by a service is exposed as a safe 403 response.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = {CustomerController.class, AuditLogController.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import({SecurityConfig.class, JwtProvider.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ProtectedReadEndpointsWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private CustomerHistoryService customerHistoryService;
    @MockitoBean
    private AuditLogService auditLogService;

    @Test
    void customerHistoryRejectsUnauthenticatedRequestsBeforeServiceAccess() throws Exception {
        mockMvc.perform(get("/api/customers/42/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));

        verifyNoInteractions(customerHistoryService);
    }

    @Test
    void branchCashierCanReadCustomerHistoryAndPaginationIsForwarded() throws Exception {
        CustomerHistoryDTO response = new CustomerHistoryDTO();
        response.setCustomerId(42L);
        response.setCustomerName("Khách hàng test");
        response.setPage(1);
        response.setPageSize(10);
        response.setTotalOrders(0);
        response.setTotalPages(0);
        response.setOrders(List.of());
        when(customerHistoryService.getCustomerHistory(42L, 1, 10)).thenReturn(response);

        mockMvc.perform(get("/api/customers/42/history")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("cashier@example.com", "ROLE_BRANCH_CASHIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(42))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(10));

        verify(customerHistoryService).getCustomerHistory(42L, 1, 10);
    }

    @Test
    void storeAdminCannotReadTheGlobalAuditFeed() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("admin@store.example", "ROLE_STORE_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verifyNoInteractions(auditLogService);
    }

    @Test
    void branchCashierCannotReadStoreAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit-logs/store/7")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("cashier@example.com", "ROLE_BRANCH_CASHIER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verifyNoInteractions(auditLogService);
    }

    @Test
    void storeManagerCannotReadStoreAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit-logs/store/7")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("manager@example.com", "ROLE_STORE_MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verifyNoInteractions(auditLogService);
    }

    @Test
    void systemAdminCanReadTheGlobalAuditFeed() throws Exception {
        AuditLogDTO log = new AuditLogDTO();
        log.setId(101L);
        log.setAction("ORDER_CREATED");
        AuditLogPageDTO page = new AuditLogPageDTO();
        page.setPage(0);
        page.setPageSize(50);
        page.setTotalElements(1);
        page.setTotalPages(1);
        page.setLogs(List.of(log));
        AuditLogFilter defaultFilter = new AuditLogFilter(0, 50, null, null, null, null);
        when(auditLogService.getAllAuditLogs(null, defaultFilter)).thenReturn(page);

        mockMvc.perform(get("/api/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("root@example.com", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs[0].id").value(101))
                .andExpect(jsonPath("$.logs[0].action").value("ORDER_CREATED"));

        verify(auditLogService).getAllAuditLogs(null, defaultFilter);
    }

    private String bearerFor(String email, String... roles) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
        return "Bearer " + jwtProvider.generateToken(authentication);
    }
}
