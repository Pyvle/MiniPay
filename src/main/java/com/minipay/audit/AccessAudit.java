package com.minipay.audit;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "access_audit")
public class AccessAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_source", nullable = false, length = 50, updatable = false)
    private ActorSource actorSource;

    @Column(name = "action", nullable = false, length = 100, updatable = false)
    private String action;

    @Column(name = "target_user_id", updatable = false)
    private Long targetUserId;

    @Column(name = "target_role_id", updatable = false)
    private Long targetRoleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb", updatable = false)
    private JsonNode beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb", updatable = false)
    private JsonNode afterData;

    protected AccessAudit() {}

    public AccessAudit(Instant createdAt, Long actorUserId, ActorSource actorSource,
            String action, Long targetUserId, Long targetRoleId,
            JsonNode beforeData, JsonNode afterData) {
        this.createdAt = createdAt;
        this.actorUserId = actorUserId;
        this.actorSource = actorSource;
        this.action = action;
        this.targetUserId = targetUserId;
        this.targetRoleId = targetRoleId;
        this.beforeData = beforeData;
        this.afterData = afterData;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getActorUserId() { return actorUserId; }
    public ActorSource getActorSource() { return actorSource; }
    public String getAction() { return action; }
    public Long getTargetUserId() { return targetUserId; }
    public Long getTargetRoleId() { return targetRoleId; }
    public JsonNode getBeforeData() { return beforeData; }
    public JsonNode getAfterData() { return afterData; }
}
