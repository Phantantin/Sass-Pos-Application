package com.tindev.controller;

import com.tindev.configuration.JwtProvider;
import com.tindev.configuration.SecurityConfig;
import com.tindev.exceptions.RestAccessDeniedHandler;
import com.tindev.exceptions.RestAuthenticationEntryPoint;
import com.tindev.service.OrderService;
import com.tindev.service.RefundService;
import com.tindev.service.ShiftReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transactional endpoints must be limited to an assigned branch operator.
 * Service-level tenant checks remain the second line of defense.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = {OrderController.class, RefundController.class, ShiftReportController.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import({SecurityConfig.class, JwtProvider.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class TransactionalRoleAuthorizationWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private RefundService refundService;
    @MockitoBean
    private ShiftReportService shiftReportService;

    @Test
    void storeManagerCannotCreateSalesRefundsOrShifts() throws Exception {
        String token = bearerFor("manager@example.com", "ROLE_STORE_MANAGER");

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(post("/api/refunds")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(post("/api/shift-reports/start")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verifyNoInteractions(orderService, refundService, shiftReportService);
    }

    private String bearerFor(String email, String role) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role)));
        return "Bearer " + jwtProvider.generateToken(authentication);
    }
}
