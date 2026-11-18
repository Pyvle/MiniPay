package com.minipay.audit;

import com.minipay.audit.dto.AccessAuditResponse;
import com.minipay.common.PageResponse;
import com.minipay.common.exception.InvalidPageRequestException;
import com.minipay.permission.PermissionCode;
import com.minipay.permission.PermissionService;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessAuditService {
    private final PermissionService permissionService;
    private final AccessAuditRepository accessAuditRepository;

    public AccessAuditService(AccessAuditRepository accessAuditRepository, PermissionService permissionService) {
        this.accessAuditRepository = accessAuditRepository;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true) 
    public PageResponse<AccessAuditResponse> list(Long actorId, int page, int size) {
        if (!permissionService.hasPermission(actorId, PermissionCode.ACCESS_AUDIT_READ)) {
            throw new AccessDeniedException("Access denied");
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPageRequestException();
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var events = accessAuditRepository.findAll(PageRequest.of(page, size, sort));
        return PageResponse.from(events.map(AccessAuditResponse::from));
    }
}
