package com.smartassign.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findTop10ByEventTypeInOrderByCreatedAtDesc(List<String> eventTypes);

    List<AuditLog> findByClaimNumberOrderByCreatedAtDesc(String claimNumber);
}
