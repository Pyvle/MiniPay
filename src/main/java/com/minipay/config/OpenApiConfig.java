package com.minipay.config;

import java.util.List;
import java.util.Set;

import com.minipay.common.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OpenApiConfig {
    private static final String BEARER = "bearerAuth";
    private static final Set<String> PUBLIC_AUTH_POSTS = Set.of(
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    @Bean
    OpenAPI miniPayOpenApi() {
        var components = new Components().addSecuritySchemes(BEARER,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token из POST /api/auth/login или /api/auth/refresh."));
        ModelConverters.getInstance().read(ErrorResponse.class).forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info().title("MiniPay API").description(
                        "init и recover запускаются из интерактивной консоли. Для них нет HTTP-маршрутов."))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    OpenApiCustomizer miniPaySecurityDocumentation() {
        return api -> api.getPaths().forEach((path, item) -> {
            item.readOperationsMap().forEach((method, operation) -> {
                if (!("POST".equals(method.name()) && PUBLIC_AUTH_POSTS.contains(path))) {
                    error(operation, "401", "Нужен действующий access token и активная сессия");
                    if (path.startsWith("/api/admin/") || path.equals("/api/users")) {
                        error(operation, "403", "Недостаточно полномочий для действия");
                    }
                    if (path.startsWith("/api/admin/") && path.contains("{")
                            && !path.endsWith("/block") && !path.endsWith("/unblock")) {
                        error(operation, "404", "Запрошенный пользователь или роль не найдены");
                    }
                    if ((path.startsWith("/api/admin/roles") && ("POST".equals(method.name())
                            || "DELETE".equals(method.name()) || path.endsWith("/name")))
                            || (path.startsWith("/api/admin/users/") && (path.endsWith("/block")
                            || path.endsWith("/super-admin")
                            || path.contains("/extra-permissions/")))) {
                        error(operation, "409", "Действие конфликтует с текущим состоянием");
                    }
                    return;
                }
                operation.setSecurity(List.of());
                error(operation, "400", "Некорректное тело запроса");
                if (path.endsWith("/register")) {
                    error(operation, "409", "Email уже зарегистрирован");
                } else if (path.endsWith("/login") || path.endsWith("/refresh")) {
                    error(operation, "401", "Учётные данные или refresh token не приняты");
                }
            });
        });
    }

    private static void error(Operation operation, String status, String description) {
        operation.getResponses().putIfAbsent(status, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
    }
}
