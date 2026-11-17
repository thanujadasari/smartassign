package com.smartassign.assignment;

import com.smartassign.common.CurrentUser;
import com.smartassign.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService service;

    public AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AssignmentDtos.AssignmentResponse> list(@RequestParam(required = false) Long claimId,
                                                                @RequestParam(required = false) Assignment.Status status,
                                                                @RequestParam(required = false) Long handlerId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "25") int size) {
        return service.list(new AssignmentService.AssignmentFilter(claimId, status, handlerId),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    public AssignmentDtos.AssignmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<AssignmentDtos.AssignmentResponse> create(@Valid @RequestBody AssignmentDtos.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, CurrentUser.username()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public AssignmentDtos.AssignmentResponse approve(@PathVariable Long id,
                                                     @Valid @RequestBody(required = false) AssignmentDtos.ApproveRequest req) {
        return service.approve(id, req, CurrentUser.username());
    }

    @PostMapping("/{id}/override")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public AssignmentDtos.AssignmentResponse override(@PathVariable Long id, @Valid @RequestBody AssignmentDtos.OverrideRequest req) {
        return service.override(id, req, CurrentUser.username());
    }

    @PostMapping("/{id}/manual-review")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public AssignmentDtos.AssignmentResponse manualReview(@PathVariable Long id, @Valid @RequestBody AssignmentDtos.ManualReviewRequest req) {
        return service.manualReview(id, req, CurrentUser.username());
    }
}
