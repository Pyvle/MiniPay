package com.minipay.support;

import java.time.Instant;

import com.minipay.permission.Permission;
import com.minipay.role.Role;
import com.minipay.session.Session;
import com.minipay.user.User;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
public abstract class RepositoryTestSupport {
    protected static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
    @Autowired protected EntityManager entityManager;
    @Autowired protected JdbcTemplate jdbc;

    protected Role role(String name) {
        Role role = new Role(name, 10);
        entityManager.persist(role);
        entityManager.flush();
        return role;
    }

    protected Permission permission(String code) {
        Permission permission = new Permission(code);
        entityManager.persist(permission);
        entityManager.flush();
        return permission;
    }

    protected User user(String email) {
        User user = TestUsers.create("Alice", email);
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    protected Session session() {
        Session session = new Session(user("session@example.com"), CREATED, CREATED.plusSeconds(3600));
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
