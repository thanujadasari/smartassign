package com.smartassign.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimStatusEventRepository extends JpaRepository<ClaimStatusEvent, Long> {

    List<ClaimStatusEvent> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);
}
