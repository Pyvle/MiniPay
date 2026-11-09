package com.minipay.installation;

import com.minipay.session.SessionRevocationService;
import java.nio.CharBuffer;
import java.time.Instant;
import java.util.Locale;

import org.apache.commons.lang3.Validate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.minipay.audit.AccessAudit;
import com.minipay.audit.AccessAuditRepository;
import com.minipay.audit.ActorSource;
import com.minipay.auth.dto.RegisterRequest;
import com.minipay.common.exception.EmailAlreadyExistsException;
import com.minipay.user.SystemRole;
import com.minipay.user.User;
import com.minipay.user.UserRepository;
import com.minipay.user.UserState;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validator;

@Service
public class InstallationService {
    private final SessionRevocationService sessionRevocationService;
    private final UserRepository userRepository;
    private final InstallationStateRepository installationStateRepository;
    private final AccessAuditRepository accessAuditRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    @PersistenceContext
    private EntityManager entityManager;

    public InstallationService(
            UserRepository userRepository,
            InstallationStateRepository installationStateRepository,
            AccessAuditRepository accessAuditRepository,
            PasswordEncoder passwordEncoder,
            Validator validator, SessionRevocationService sessionRevocationService) {
        this.userRepository = userRepository;
        this.installationStateRepository = installationStateRepository;
        this.accessAuditRepository = accessAuditRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Transactional
    public User initialize(String name, String email, char[] password) {
        if (name == null || email == null || password == null) {
            throw new IllegalArgumentException("Имя, email и пароль обязательны");
        }
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        if (!validator.validateValue(RegisterRequest.class, "name", name).isEmpty()) {
            throw new IllegalArgumentException("Имя должно быть непустым и содержать не более 100 символов");
        }
        if (!validator.validateValue(RegisterRequest.class, "email", normalizedEmail).isEmpty()) {
            throw new IllegalArgumentException("Укажите корректный email длиной не более 255 символов");
        }
        if (password.length < 8 || password.length > 128
                || CharBuffer.wrap(password).chars().allMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "Пароль должен содержать 8–128 символов и не состоять только из пробелов");
        }
        if (installationStateRepository.existsById(1L)) {
            throw new IllegalStateException("Первоначальная настройка уже выполнена");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        Instant now = Instant.now();
        entityManager.persist(new InstallationState(now));
        entityManager.flush();

        User user = new User(name, normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(CharBuffer.wrap(password)));
        user.setSystemRole(SystemRole.SUPER_ADMIN);
        user.setState(UserState.ACTIVE);
        userRepository.saveAndFlush(user);

        var afterData = JsonNodeFactory.instance.objectNode()
                .put("systemRole", SystemRole.SUPER_ADMIN.name())
                .put("state", UserState.ACTIVE.name());
        accessAuditRepository.saveAndFlush(new AccessAudit(
                now, null, ActorSource.CLI, "SUPER_ADMIN_INIT",
                user.getId(), null, null, afterData));
        return user;
    }

    @Transactional
    public void recover(String email, char[] newPassword) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        if (!validator.validateValue(RegisterRequest.class, "email", normalizedEmail).isEmpty()) {
            throw new IllegalArgumentException("Email must be valid and at most 255 characters long");
        }

        if (newPassword == null || newPassword.length < 8 || newPassword.length > 128
                || CharBuffer.wrap(newPassword).chars().allMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "Password must be 8 to 128 characters long and must not be blank");
        }

        User user = userRepository.findByEmailForUpdate(normalizedEmail)
                .filter(candidate -> candidate.getSystemRole() == SystemRole.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("SUPER_ADMIN account not found"));

        var beforeData = JsonNodeFactory.instance.objectNode()
                .put("state", user.getState().name());

        user.setPasswordHash(passwordEncoder.encode(CharBuffer.wrap(newPassword)));
        user.setState(UserState.ACTIVE);
        sessionRevocationService.revokeAllForUser(user.getId(), "SUPER_ADMIN_RECOVERY");

        var afterData = JsonNodeFactory.instance.objectNode()
                .put("state", user.getState().name());

        accessAuditRepository.saveAndFlush(new AccessAudit(
                Instant.now(), null, ActorSource.CLI, "SUPER_ADMIN_RECOVER",
                user.getId(), null, beforeData, afterData));
    }
}
