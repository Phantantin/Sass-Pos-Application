package com.tindev.configuration;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 1. Cấu hình quản lý phiên (Session): Không lưu trạng thái trên Server (Stateless)
                // Phù hợp cho hệ thống dùng JWT Token thay vì Session/Cookie truyền thống.
                .sessionManagement(management ->
                        management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 2. Cấu hình phân quyền (Authorization) theo URL và Role (Vai trò)
                .authorizeHttpRequests(authorize -> authorize
                        // Quy tắc cụ thể: URL cho Super Admin quản lý toàn hệ thống SaaS
                        .requestMatchers("/api/super-admin/**").hasRole("ADMIN")

                        // Quy tắc cụ thể: URL cho Chủ cửa hàng (Store Owner)
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "STORE_OWNER")

                        // Quy tắc cụ thể: URL cho Nhân viên thu ngân (Cashier)
                        .requestMatchers("/api/cashier/**").hasAnyRole("ADMIN", "STORE_OWNER", "CASHIER")

                        // Quy tắc chung: Tất cả các API khác bắt đầu bằng /api/ đều phải ĐĂNG NHẬP
                        .requestMatchers("/api/**").authenticated()

                        // Cho phép truy cập tự do vào các URL khác (như Login, Register, Public info)
                        .anyRequest().permitAll()
                )

                // 3. Đăng ký Filter tự định nghĩa (JwtValidation) để kiểm tra tính hợp lệ của Token
                // Filter này sẽ chạy TRƯỚC BasicAuthenticationFilter
                .addFilterBefore(new JwtValidation(), BasicAuthenticationFilter.class)

                // 4. Vô hiệu hóa bảo vệ CSRF (vì dùng JWT nên không cần thiết, giúp gọi API dễ hơn)
                .csrf(AbstractHttpConfigurer::disable)

                // 5. Cấu hình CORS: Cho phép Frontend từ domain khác gọi API tới Backend
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .build();
    }

    /**
     * Cấu hình chi tiết về CORS (Cross-Origin Resource Sharing)
     * Giúp React (Port 5173/3000) có thể giao tiếp được với Spring Boot (Port 8080)
     */
    private CorsConfigurationSource corsConfigurationSource() {
        return new CorsConfigurationSource() {
            @Override
            public @Nullable CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                CorsConfiguration cfg = new CorsConfiguration();

                // Danh sách các địa chỉ Frontend được phép truy cập
                cfg.setAllowedOrigins(
                        Arrays.asList(
                                "http://localhost:5173", // Vite/React
                                "http://localhost:3000"  // Create React App
                        )
                );

                // Cho phép tất cả các phương thức HTTP (GET, POST, PUT, DELETE,...)
                cfg.setAllowedMethods(Collections.singletonList("*"));

                // Cho phép gửi kèm thông tin xác thực (như Cookies hoặc Auth Headers)
                cfg.setAllowCredentials(true);

                // Cho phép tất cả các Headers từ phía Client gửi lên
                cfg.setAllowedHeaders(Collections.singletonList("*"));

                // Tiết lộ Header "Authorization" để Frontend có thể đọc và lưu JWT Token
                cfg.setExposedHeaders(Arrays.asList("Authorization"));

                // Thời gian lưu cấu hình CORS này tại trình duyệt (1 tiếng)
                cfg.setMaxAge(3600L);

                return cfg;
            }
        };
    }
}