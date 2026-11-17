package com.minipay.permission;

public enum PermissionCode {
    USER_LIST(true),
    PROFILE_READ_SELF(false),
    PROFILE_READ_ANY(true),
    WALLET_CREATE_SELF(false),
    WALLET_READ_SELF(false),
    WALLET_READ_ANY(true),
    WALLET_DEPOSIT_SELF(false),
    WALLET_WITHDRAW_SELF(false),
    WALLET_TRANSFER_SELF(false),
    HISTORY_READ_SELF(false),
    HISTORY_READ_ANY(true),
    TRANSACTION_READ_OWN(false),
    TRANSACTION_READ_ANY(true),
    USER_BLOCK(true),
    USER_UNBLOCK(true),
    MAIN_ROLE_ASSIGN(true),
    MAIN_ROLE_REMOVE(true),
    EXTRA_PERMISSION_GRANT(true),
    EXTRA_PERMISSION_REVOKE(true),
    ACCESS_AUDIT_READ(true);

    private final boolean assignableToServiceRole;

    PermissionCode(boolean assignableToServiceRole) {
        this.assignableToServiceRole = assignableToServiceRole;
    }

    public boolean isAssignableToServiceRole() {
        return assignableToServiceRole;
    }
}
