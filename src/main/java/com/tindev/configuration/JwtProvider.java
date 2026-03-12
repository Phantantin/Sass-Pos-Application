package com.tindev.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.*;

@Service
public class JwtProvider {
    // Tạo chìa khóa bảo mật từ chuỗi Secret đã khai báo
    static SecretKey key = Keys.hmacShaKeyFor(JwtConstant.JWT_SECRET.getBytes());

    // Hàm tạo Token dựa trên thông tin đăng nhập thành công
    public String generateToken(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        // Chuyển đổi danh sách quyền (Roles) thành chuỗi ngăn cách bởi dấu phẩy
        String roles = populateAuthorities(authorities);

        return Jwts.builder()
                .issuedAt(new Date()) // Thời điểm tạo thẻ
                .expiration(new Date(new Date().getTime() + 8400000)) // Thời hạn thẻ (khoảng 2.3 giờ)
                .claim("email", authentication.getName()) // Lưu email vào thẻ
                .claim("authorities", roles) // Lưu danh sách quyền vào thẻ
                .signWith(key) // Ký tên bằng chìa khóa bí mật để tránh giả mạo
                .compact();
    }

    // Hàm lấy Email từ một Token có sẵn
    public String getEmailFromToken(String jwt) {
        jwt = jwt.substring(7); // Loại bỏ chữ "Bearer " để lấy mã JWT thực tế
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(jwt)
                .getPayload(); // Giải mã thẻ để lấy nội dung bên trong

        return String.valueOf(claims.get("email"));
    }

    // Chuyển danh sách quyền từ Spring Security thành chuỗi String (ví dụ: "ROLE_ADMIN,ROLE_CASHIER")
    private String populateAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<String> auths = new HashSet<>();
        for (GrantedAuthority authority : authorities) {
            auths.add(authority.getAuthority());
        }
        return String.join(",", auths);
    }
}