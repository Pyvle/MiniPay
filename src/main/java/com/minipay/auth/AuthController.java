package com.minipay.auth;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minipay.auth.dto.RegisterRequest;
import com.minipay.common.exception.InvalidRefreshTokenException;
import com.minipay.auth.dto.LoginRequest;
import com.minipay.auth.dto.LoginResponse;
import com.minipay.auth.dto.RefreshRequest;
import com.minipay.user.User;
import com.minipay.user.UserResponse;
import com.minipay.user.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final LogoutService logoutService;
    private final AuthService authService;
    private final UserService userService;
    private final RefreshService refreshService;

    public AuthController(AuthService authService, UserService userService,
            RefreshService refreshService, LogoutService logoutService) {
        this.authService = authService;
        this.userService = userService;
        this.refreshService = refreshService;
        this.logoutService = logoutService;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return new UserResponse(userService.getUserById(userId));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getPassword());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.getName(), request.getEmail(),
                request.getPassword());
        return new UserResponse(user);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest refreshRequest) {
        return refreshService.refresh(refreshRequest.getRefreshToken())
            .orElseThrow(InvalidRefreshTokenException::new);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        logoutService.logout(request.getRefreshToken());
    }
    

}
