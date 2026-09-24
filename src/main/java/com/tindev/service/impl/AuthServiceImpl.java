package com.tindev.service.impl;

import com.tindev.configuration.JwtProvider;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.mapper.UserMapper;
import com.tindev.modal.User;
import com.tindev.payload.dto.UserDto;
import com.tindev.payload.response.AuthResponse;
import com.tindev.repository.UserRepository;
import com.tindev.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CustomUserImplementation customUserImplementation;

    @Override
    public AuthResponse signup(UserDto userDto) {
        if (userRepository.findByEmail(userDto.getEmail()) != null) throw ApiException.conflict("Email đã được sử dụng");
        User user = new User();
        user.setEmail(userDto.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setRole(UserRole.ROLE_STORE_ADMIN);
        user.setFullName(userDto.getFullName().trim());
        user.setPhone(userDto.getPhone());
        user.setLastLogin(LocalDateTime.now());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(saved.getEmail(), null,
                List.of(new SimpleGrantedAuthority(saved.getRole().name())));
        return response(saved, auth, "Đăng ký thành công");
    }

    @Override
    public AuthResponse login(UserDto userDto) {
        UserDetails details = customUserImplementation.loadUserByUsername(userDto.getEmail().trim().toLowerCase());
        if (!passwordEncoder.matches(userDto.getPassword(), details.getPassword())) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
        }
        Authentication auth = new UsernamePasswordAuthenticationToken(details.getUsername(), null, details.getAuthorities());
        User user = userRepository.findByEmail(details.getUsername());
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        return response(user, auth, "Đăng nhập thành công");
    }

    private AuthResponse response(User user, Authentication authentication, String message) {
        AuthResponse response = new AuthResponse();
        response.setJwt(jwtProvider.generateToken(authentication));
        response.setMessage(message);
        response.setUser(UserMapper.toDTO(user));
        return response;
    }
}
