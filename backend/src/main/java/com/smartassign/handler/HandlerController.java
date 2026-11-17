package com.smartassign.handler;

import com.smartassign.common.CurrentUser;
import com.smartassign.common.PageResponse;
import com.smartassign.domain.Expertise;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/handlers")
public class HandlerController {

    private static final Set<String> SORTABLE = Set.of("code", "fullName", "primaryExpertise", "experienceYears",
            "currentWorkload", "maxWorkload", "location", "successRate", "avgResolutionDays", "resolvedCount",
            "updatedAt");

    private final HandlerService handlers;

    public HandlerController(HandlerService handlers) {
        this.handlers = handlers;
    }

    @GetMapping
    public PageResponse<HandlerDtos.HandlerResponse> list(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) Expertise expertise,
                                                          @RequestParam(required = false) String location,
                                                          @RequestParam(required = false) Boolean available,
                                                          @RequestParam(required = false) Boolean active,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "25") int size,
                                                          @RequestParam(defaultValue = "code") String sort,
                                                          @RequestParam(defaultValue = "asc") String direction) {
        String field = SORTABLE.contains(sort) ? sort : "code";
        Sort s = Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC, field)
                .and(Sort.by("code"));
        return handlers.list(new HandlerService.HandlerFilter(q, expertise, location, available, active, status),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), s));
    }

    @GetMapping("/{id}")
    public HandlerDtos.HandlerDetail detail(@PathVariable Long id) {
        return handlers.detail(id);
    }

    /** Supervisors adjust expertise, experience, capacity and location. Availability belongs to the handler. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public HandlerDtos.HandlerResponse update(@PathVariable Long id, @Valid @RequestBody HandlerDtos.HandlerUpdateRequest req) {
        return handlers.update(id, req, CurrentUser.username());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public HandlerDtos.HandlerResponse status(@PathVariable Long id, @Valid @RequestBody HandlerDtos.StatusRequest req) {
        return handlers.setActive(id, req, CurrentUser.username());
    }
}
