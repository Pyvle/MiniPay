package com.minipay.role.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
    @NotBlank @Size(max = 50) String name,
    @NotNull @Positive Integer rank,
    @NotNull List< @NotBlank String> permissionCodes
) {}
