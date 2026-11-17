package com.smartassign.customer;

import com.smartassign.auth.AuthService;
import com.smartassign.claim.ClaimDtos;
import com.smartassign.claim.ClaimService;
import com.smartassign.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customer portal (URL security: CUSTOMER only). A customer only ever sees their own claims. */
@RestController
@RequestMapping("/api/customer/claims")
public class CustomerController {

    private final ClaimService claims;
    private final AuthService auth;

    public CustomerController(ClaimService claims, AuthService auth) {
        this.claims = claims;
        this.auth = auth;
    }

    @PostMapping
    public ResponseEntity<ClaimDtos.CustomerClaimDetail> submit(@Valid @RequestBody ClaimDtos.CustomerClaimRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(claims.submit(auth.currentUser(), req));
    }

    @GetMapping
    public PageResponse<ClaimDtos.CustomerClaim> mine(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return claims.customerClaims(auth.currentUser(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @GetMapping("/{id}")
    public ClaimDtos.CustomerClaimDetail one(@PathVariable Long id) {
        return claims.customerClaim(auth.currentUser(), id);
    }
}
