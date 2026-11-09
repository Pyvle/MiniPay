package com.minipay.user;

import com.minipay.common.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.util.List;

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
