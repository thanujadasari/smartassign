package com.smartassign.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RankingSettingsRepository extends JpaRepository<RankingSettings, Long> {

    Optional<RankingSettings> findFirstByActiveTrue();

    List<RankingSettings> findTop20ByOrderByCreatedAtDesc();

    /** Bulk update executes immediately, before the new active row is inserted (partial unique index). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RankingSettings s set s.active = false where s.active = true")
    int deactivateAll();
}
