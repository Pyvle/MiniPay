package com.minipay.audit;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.minipay.role.Role;
import com.minipay.support.RepositoryTestSupport;
import com.minipay.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessAuditRepositoryTest extends RepositoryTestSupport {
    @Autowired private AccessAuditRepository repository;

    @Test
    void userEventAndJsonSnapshotsShouldSurviveReload() {
        User actor = user("actor@example.com");
        User target = user("target@example.com");
        var before = JsonNodeFactory.instance.objectNode().put("state", "ACTIVE").put("rank", 1);
        var after = JsonNodeFactory.instance.objectNode().put("state", "BLOCKED").put("rank", 1);
        AccessAudit event = repository.saveAndFlush(new AccessAudit(CREATED, actor.getId(), ActorSource.USER,
                "USER_BLOCK", target.getId(), null, before, after));
        flushAndClear();
        AccessAudit loaded = repository.findById(event.getId()).orElseThrow();
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED);
        assertThat(loaded.getActorUserId()).isEqualTo(actor.getId());
        assertThat(loaded.getActorSource()).isEqualTo(ActorSource.USER);
        assertThat(loaded.getAction()).isEqualTo("USER_BLOCK");
        assertThat(loaded.getTargetUserId()).isEqualTo(target.getId());
        assertThat(loaded.getBeforeData()).isEqualTo(before);
        assertThat(loaded.getAfterData()).isEqualTo(after);
    }

    @Test
    void cliEventMayHaveNoActorAndNoSnapshots() {
        AccessAudit event = repository.saveAndFlush(new AccessAudit(
                CREATED, null, ActorSource.CLI, "INIT", null, null, null, null));
        flushAndClear();
        AccessAudit loaded = repository.findById(event.getId()).orElseThrow();
        assertThat(loaded.getActorSource()).isEqualTo(ActorSource.CLI);
        assertThat(loaded.getActorUserId()).isNull();
        assertThat(loaded.getBeforeData()).isNull();
        assertThat(loaded.getAfterData()).isNull();
    }

    @Test
    void userSourceWithoutActorShouldBeRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(new AccessAudit(
                CREATED, null, ActorSource.USER, "USER_BLOCK", null, null, null, null)));
    }

    @Test
    void unknownSourceShouldBeRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO access_audit (created_at, actor_source, action) VALUES (?, ?, ?)",
                java.sql.Timestamp.from(CREATED), "UNKNOWN", "INIT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankActionShouldBeRejected(String action) {
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(new AccessAudit(
                CREATED, null, ActorSource.CLI, action, null, null, null, null)));
    }

    @Test
    void deletingReferencedRoleShouldPreserveEventAndSnapshot() {
        Role role = role("Support");
        var before = JsonNodeFactory.instance.objectNode().put("name", "Support").put("rank", 10);
        AccessAudit event = repository.saveAndFlush(new AccessAudit(
                CREATED, null, ActorSource.CLI, "ROLE_DELETE", null, role.getId(), before, null));
        entityManager.remove(role);
        flushAndClear();
        AccessAudit loaded = repository.findById(event.getId()).orElseThrow();
        assertThat(entityManager.find(Role.class, role.getId())).isNull();
        assertThat(loaded.getTargetRoleId()).isEqualTo(role.getId());
        assertThat(loaded.getBeforeData()).isEqualTo(before);
        assertThat(loaded.getAfterData()).isNull();
        assertThat(loaded.getAction()).isEqualTo("ROLE_DELETE");
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED);
        assertThat(loaded.getActorSource()).isEqualTo(ActorSource.CLI);
    }
}
