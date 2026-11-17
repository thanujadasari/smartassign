package com.smartassign.audit;

import com.smartassign.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('SUPERVISOR')")
public class AuditController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "eventType", "claimNumber", "actorUsername",
            "supervisorAction", "recommendationScore");

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditDto> search(@RequestParam(required = false) String claimNumber,
                                         @RequestParam(required = false) String handlerCode,
                                         @RequestParam(required = false) String eventType,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String actor,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size,
                                         @RequestParam(defaultValue = "createdAt") String sort,
                                         @RequestParam(defaultValue = "desc") String direction) {
        String sortField = SORTABLE.contains(sort) ? sort : "createdAt";
        Sort s = Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, sortField);
        return audit.search(filter(claimNumber, handlerCode, eventType, action, actor, from, to, q),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), s));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String claimNumber,
                                         @RequestParam(required = false) String handlerCode,
                                         @RequestParam(required = false) String eventType,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String actor,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String q) {
        String csv = audit.exportCsv(filter(claimNumber, handlerCode, eventType, action, actor, from, to, q));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"smartassign-audit-" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static AuditService.AuditFilter filter(String claimNumber, String handlerCode, String eventType, String action,
                                                   String actor, LocalDate from, LocalDate to, String q) {
        Instant f = from == null ? null : from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant t = to == null ? null : to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        return new AuditService.AuditFilter(claimNumber, handlerCode, eventType, action, actor, f, t, q);
    }
}
