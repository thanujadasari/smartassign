package com.smartassign.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EligibilityRuleRepository extends JpaRepository<EligibilityRule, String> {

    List<EligibilityRule> findAllByOrderBySortOrderAsc();
}
