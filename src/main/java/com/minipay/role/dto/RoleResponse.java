package com.minipay.role.dto;

import java.util.List;

public record RoleResponse(
    Long id,
    String name,
    int rank,
    List<String> permissionCodes
) {}
