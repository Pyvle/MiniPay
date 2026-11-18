package com.minipay.audit;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minipay.audit.dto.AccessAuditResponse;
import com.minipay.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/admin/access-audit")
public class AccessAuditController {
    private final AccessAuditService service;

    public AccessAuditController(AccessAuditService service) {
        this.service = service;
    }

    @Operation(summary = "Прочитать журнал доступа",
            description = "Для SUPER_ADMIN и сотрудников с ACCESS_AUDIT_READ. События от новых к старым, размер страницы от 1 до 100.")
    @GetMapping
    public PageResponse<AccessAuditResponse> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        Long actorId = Long.valueOf(authentication.getName());
        return service.list(actorId, page, size);
    }

}
