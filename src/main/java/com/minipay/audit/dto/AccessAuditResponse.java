package com.minipay.audit.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.minipay.audit.AccessAudit;
import com.minipay.audit.ActorSource;

public record AccessAuditResponse(
        Long id,
        Instant createdAt,
        ActorSource actorSource,
        Long actorUserId,
        String action,
        Long targetUserId,
        Long targetRoleId,
        JsonNode beforeData,
        JsonNode afterData) {
    public static AccessAuditResponse from(AccessAudit event) {
        return new AccessAuditResponse(
                event.getId(),
                event.getCreatedAt(),
                event.getActorSource(),
                event.getActorUserId(),
                event.getAction(),
                event.getTargetUserId(),
                event.getTargetRoleId(),
                event.getBeforeData(),
                event.getAfterData());
    }
}
