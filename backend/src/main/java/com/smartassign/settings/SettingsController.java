package com.smartassign.settings;

import com.smartassign.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/ranking-weights")
    public SettingsDtos.WeightsResponse weights() {
        return settings.getWeights();
    }

    @GetMapping("/ranking-weights/history")
    public List<SettingsDtos.WeightsResponse> history() {
        return settings.weightHistory();
    }

    @PutMapping("/ranking-weights")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SettingsDtos.WeightsResponse updateWeights(@Valid @RequestBody SettingsDtos.WeightsRequest req) {
        return settings.updateWeights(req, CurrentUser.username());
    }

    @GetMapping("/eligibility-rules")
    public List<SettingsDtos.RuleResponse> rules() {
        return settings.rules();
    }

    @PutMapping("/eligibility-rules/{code}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SettingsDtos.RuleResponse updateRule(@PathVariable String code, @Valid @RequestBody SettingsDtos.RuleUpdateRequest req) {
        return settings.updateRule(code, req, CurrentUser.username());
    }
}
