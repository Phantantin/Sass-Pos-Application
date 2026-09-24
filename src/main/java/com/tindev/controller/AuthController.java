package com.tindev.controller;

import com.tindev.payload.dto.LoginRequest;
import com.tindev.payload.dto.SignupRequest;
import com.tindev.payload.dto.UserDto;
import com.tindev.payload.response.AuthResponse;
import com.tindev.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signupHandler(@Valid @RequestBody SignupRequest request) throws Exception {
        UserDto user = new UserDto();
        user.setFullName(request.fullName()); user.setEmail(request.email());
        user.setPassword(request.password()); user.setPhone(request.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginHandler(@Valid @RequestBody LoginRequest request) throws Exception {
        UserDto user = new UserDto();
        user.setEmail(request.email()); user.setPassword(request.password());
        return ResponseEntity.ok(authService.login(user));
    }
}
