package com.minipay.common.exception;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(Long roleId) {
        super("Role not found: " + roleId);
    }
}
