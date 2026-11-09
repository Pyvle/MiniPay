package com.minipay.permission;

import com.minipay.support.RepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionRepositoryTest extends RepositoryTestSupport {
    @Autowired private PermissionRepository repository;

    @Test
    void codeShouldBePersistedAndFound() {
        Permission saved = repository.saveAndFlush(new Permission("TEST_READ"));
        flushAndClear();
        Permission loaded = repository.findByCode("TEST_READ").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getCode()).isEqualTo("TEST_READ");
    }

    @Test
    void duplicateCodeShouldBeRejected() {
        repository.saveAndFlush(new Permission("TEST_READ"));
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new Permission("TEST_READ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "test_read", " TEST_READ", "TEST_READ "})
    void invalidCodeShouldBeRejected(String code) {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new Permission(code)));
    }

    @Test
    void migrationShouldSeedExactlyTwentyPermissions() {
        assertThat(repository.findAll()).extracting(Permission::getCode).containsExactlyInAnyOrder(
                "USER_LIST", "PROFILE_READ_SELF", "PROFILE_READ_ANY", "WALLET_CREATE_SELF",
                "WALLET_READ_SELF", "WALLET_READ_ANY", "WALLET_DEPOSIT_SELF", "WALLET_WITHDRAW_SELF",
                "WALLET_TRANSFER_SELF", "HISTORY_READ_SELF", "HISTORY_READ_ANY", "TRANSACTION_READ_OWN",
                "TRANSACTION_READ_ANY", "USER_BLOCK", "USER_UNBLOCK", "MAIN_ROLE_ASSIGN",
                "MAIN_ROLE_REMOVE", "EXTRA_PERMISSION_GRANT", "EXTRA_PERMISSION_REVOKE", "ACCESS_AUDIT_READ");
    }
}
