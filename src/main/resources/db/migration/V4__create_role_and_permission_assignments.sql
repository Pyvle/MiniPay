CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),

    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_extra_permissions (
    user_id BIGINT NOT NULL REFERENCES users(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),

    PRIMARY KEY (user_id, permission_id)
);

CREATE TABLE role_assignable_roles (
    role_id BIGINT NOT NULL REFERENCES roles(id),
    assignable_role_id BIGINT NOT NULL REFERENCES roles(id),

    PRIMARY KEY (role_id, assignable_role_id)
);

CREATE TABLE role_grantable_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),

    PRIMARY KEY (role_id, permission_id)
);