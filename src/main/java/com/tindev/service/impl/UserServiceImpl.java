package com.tindev.service.impl;

import com.tindev.configuration.JwtProvider;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.modal.User;
import com.tindev.repository.UserRepository;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Override
    public User getUserFromJwtToken(String token) throws UserException {
        String email = jwtProvider.getEmailFromToken(token);
        User user = userRepository.findByEmail(email);
        if(user == null){
            throw new UserException("Invalid token");
        }
        return user;
    }

    @Override
    public User getCurrentUser() throws UserException {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if(user == null){
            throw new UserException("User not found");
        }
        return user;
    }

    @Override
    public User getUserByEmail(String email) throws UserException {
        User user = userRepository.findByEmail(email);
        if(user == null){
            throw new UserException("User not found");
        }
        return user;
    }

    @Override
    public User getUserById(Long id) throws UserException {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new UserException("User not found"));
        User current = getCurrentUser();
        if (current.getRole() != UserRole.ROLE_ADMIN && !Objects.equals(current.getId(), target.getId())) {
            throw ApiException.forbidden("Bạn chỉ có thể xem hồ sơ của chính mình");
        }
        return target;
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
