package com.minipay.role;

import com.minipay.support.RepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleRepositoryTest extends RepositoryTestSupport {
    @Autowired private RoleRepository repository;

    @Test
    void positiveRankAndNoEmployeesShouldBeAllowed() {
        Role saved = repository.saveAndFlush(new Role("Support", 1));
        flushAndClear();
        Role loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Support");
        assertThat(loaded.getRank()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE main_role_id = ?",
                Long.class, saved.getId())).isZero();
    }

    @Test
    void differentRolesMayHaveSameRank() {
        repository.saveAndFlush(new Role("Support", 10));
        Role second = repository.saveAndFlush(new Role("Accounting", 10));
        flushAndClear();
        assertThat(repository.findById(second.getId()).orElseThrow().getRank()).isEqualTo(10);
        assertThat(repository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveRankShouldBeRejected(int rank) {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new Role("Support", rank)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "USER", "user", " User ",
            "SUPER_ADMIN", "super_admin", " Super_Admin "})
    void emptyOrReservedNameShouldBeRejected(String name) {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new Role(name, 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Support", "support", " Support "})
    void normalizedDuplicateNameShouldBeRejected(String name) {
        repository.saveAndFlush(new Role("Support", 1));
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new Role(name, 2)));
    }

    @Test
    void renamingToOccupiedNameShouldBeRejected() {
        repository.saveAndFlush(new Role("Support", 1));
        Role role = repository.saveAndFlush(new Role("Accounting", 2));
        role.setName(" support ");
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(role));
    }
}
