package com.smartassign.handlerportal;

import com.smartassign.auth.AuthService;
import com.smartassign.claim.ClaimDtos;
import com.smartassign.common.PageResponse;
import com.smartassign.domain.ClaimStatus;
import com.smartassign.handler.HandlerDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Handler portal (URL security: HANDLER only). */
@RestController
@RequestMapping("/api/handler-portal")
public class HandlerPortalController {

    private final HandlerPortalService portal;
    private final AuthService auth;

    public HandlerPortalController(HandlerPortalService portal, AuthService auth) {
        this.portal = portal;
        this.auth = auth;
    }

    @GetMapping("/me")
    public HandlerPortalService.Profile me() {
        return portal.profile(auth.currentUser());
    }

    @PutMapping("/availability")
    public HandlerPortalService.Profile availability(@Valid @RequestBody HandlerDtos.AvailabilityRequest req) {
        return portal.setAvailability(auth.currentUser(), req);
    }

    /** view=open (assigned, default) or view=resolved */
    @GetMapping("/claims")
    public PageResponse<ClaimDtos.ClaimResponse> claims(@RequestParam(defaultValue = "open") String view,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        boolean resolved = "resolved".equalsIgnoreCase(view);
        // open claims: most urgent first, then the longest-waiting
        Sort sort = resolved ? Sort.by(Sort.Direction.DESC, "resolvedAt")
                : Sort.by(Sort.Direction.ASC, "priorityRank").and(Sort.by(Sort.Direction.ASC, "assignedAt"));
        return portal.myClaims(auth.currentUser(), List.of(resolved ? ClaimStatus.RESOLVED : ClaimStatus.ASSIGNED),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), sort.and(Sort.by("id"))));
    }

    @GetMapping("/claims/{id}")
    public ClaimDtos.ClaimDetail claim(@PathVariable Long id) {
        return portal.claim(auth.currentUser(), id);
    }

    @PostMapping("/claims/{id}/resolve")
    public ClaimDtos.ClaimDetail resolve(@PathVariable Long id, @Valid @RequestBody HandlerPortalService.ResolveRequest req) {
        return portal.resolve(auth.currentUser(), id, req);
    }
}
