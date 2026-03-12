package com.tindev.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;

public class JwtValidation extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Lấy chuỗi Token từ Header "Authorization"
        String jwt = request.getHeader(JwtConstant.JWT_HEADER);

        // Kiểm tra xem request có kèm Token không (định dạng thường là: Bearer <token>)
        if(jwt != null) {
            jwt = jwt.substring(7); // Cắt bỏ 7 ký tự đầu "Bearer "

            try {
                // Sử dụng cùng một chìa khóa bí mật để xác thực
                SecretKey key = Keys.hmacShaKeyFor(JwtConstant.JWT_SECRET.getBytes());

                // Giải mã Token để lấy Payload (dữ liệu bên trong)
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(jwt)
                        .getPayload();

                String email = String.valueOf(claims.get("email"));
                String authorities = String.valueOf(claims.get("authorities"));

                // Chuyển chuỗi quyền từ Token ngược lại thành danh sách của Spring Security
                List<GrantedAuthority> auths = AuthorityUtils.commaSeparatedStringToAuthorityList(authorities);

                // Tạo đối tượng Authentication (chứng thực thành công)
                Authentication auth = new UsernamePasswordAuthenticationToken(email, null, auths);

                // Quan trọng: Lưu thông tin này vào "Ngữ cảnh bảo mật" của hệ thống
                // Các Controller sau đó sẽ biết được User nào đang thực hiện thao tác này
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                // Nếu Token hết hạn hoặc sai lệch, ném lỗi ngay lập tức
                throw new BadCredentialsException("Invalid JWT.. ");
            }
        }

        // Cho phép Request đi tiếp đến Controller hoặc Filter tiếp theo
        filterChain.doFilter(request, response);
    }
}