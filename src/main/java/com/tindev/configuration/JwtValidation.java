package com.tindev.configuration;

import io.jsonwebtoken.Claims;
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

import java.io.IOException;
import java.util.List;

public class JwtValidation extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;

    public JwtValidation(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(JwtConstant.JWT_HEADER);
        if (header != null && !header.isBlank()) {
            try {
                Claims claims = jwtProvider.parseToken(jwtProvider.extractToken(header));
                String subject = claims.getSubject();
                String authorities = claims.get("authorities", String.class);
                List<GrantedAuthority> grantedAuthorities = AuthorityUtils
                        .commaSeparatedStringToAuthorityList(authorities == null ? "" : authorities);
                Authentication authentication = new UsernamePasswordAuthenticationToken(subject, null, grantedAuthorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception exception) {
                SecurityContextHolder.clearContext();
                throw new BadCredentialsException("JWT không hợp lệ hoặc đã hết hạn", exception);
            }
        }
        filterChain.doFilter(request, response);
    }
}
