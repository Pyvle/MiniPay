package com.minipay.user;

import com.minipay.common.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Пользователи", description = "Взаимодействие с пользователями")
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Создать нового пользователя", description = "Возвращает информацию о созданном пользователе")
    @ApiResponse(responseCode = "400", description = "Некорректный JSON, имя или email",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email уже зарегистрирован",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse creatUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.getName(), request.getEmail());
        return new UserResponse(user);
    }

    @Operation(summary = "Получить пользователя по ID", description = "Возвращает информацию о полученном пользователе")
    @ApiResponse(responseCode = "400", description = "Некорректный формат ID пользователя",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public UserResponse getUserById(
            @Parameter(description = "ID пользователя", example = "25") @PathVariable("id") Long id) {
        User user = userService.getUserById(id);
        return new UserResponse(user);
    }

    @Operation(summary = "Получить всех пользователей", description = "Возвращает информацию о всех пользователях")
    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers()
                .stream()
                .map(UserResponse::new)
                .toList();
    }

}
