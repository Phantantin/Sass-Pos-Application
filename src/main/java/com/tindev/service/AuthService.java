package com.tindev.service;

import com.tindev.exceptions.UserException;
import com.tindev.payload.dto.UserDto;
import com.tindev.payload.response.AuthResponse;

public interface AuthService {

    AuthResponse signup(UserDto userDto) throws UserException;
    AuthResponse login(UserDto userDto) throws UserException;
}
