package com.smartassign.handler;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HandlerRepository extends JpaRepository<Handler, Long>, JpaSpecificationExecutor<Handler> {

    Optional<Handler> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Optional<Handler> findByUserId(Long userId);

    /** Row lock used when changing workload, so concurrent approvals cannot over-fill a handler. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Handler h where h.id = :id")
    Optional<Handler> findByIdForUpdate(@Param("id") Long id);

    List<Handler> findAllByOrderByCodeAsc();

    long countByActiveTrueAndAvailableTrue();

    @Query(value = "SELECT nextval('handler_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
