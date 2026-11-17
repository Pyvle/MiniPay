package com.minipay.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameRoleRequest(
        @NotBlank @Size(max = 50) String name
) {}