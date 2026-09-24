package com.tindev.controller;

import com.tindev.configuration.JwtProvider;
import com.tindev.configuration.SecurityConfig;
import com.tindev.exceptions.RestAccessDeniedHandler;
import com.tindev.exceptions.RestAuthenticationEntryPoint;
import com.tindev.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(controllers = EmployeeController.class)
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import({SecurityConfig.class, JwtProvider.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class EmployeeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void storeManagerCanReadEmployeesFromTheirStore() throws Exception {
        when(employeeService.findStoreEmployees(10L, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/employees/store/10")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("manager@example.com", "ROLE_STORE_MANAGER")))
                .andExpect(status().isOk());

        verify(employeeService).findStoreEmployees(10L, null);
    }

    @Test
    void storeManagerCannotCreateStoreEmployees() throws Exception {
        mockMvc.perform(post("/api/employees/store/10")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("manager@example.com", "ROLE_STORE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Test user","email":"test@example.com","password":"password123","role":"ROLE_BRANCH_CASHIER"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoMoreInteractions(employeeService);
    }

    private String bearerFor(String email, String role) {
        var authentication = new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role)));
        return "Bearer " + jwtProvider.generateToken(authentication);
    }
}
