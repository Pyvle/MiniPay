package com.minipay.role.dto;

import java.util.List;

public record RoleOptionsResponse(List<RoleSummary> assignableRoles,
        List<String> grantablePermissionCodes,
        List<String> permissionCatalogCodes) {
    public record RoleSummary(Long id, String name, int rank) {}
}
