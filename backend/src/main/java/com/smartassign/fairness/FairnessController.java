package com.smartassign.fairness;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fairness")
public class FairnessController {

    private final FairnessService service;

    public FairnessController(FairnessService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public FairnessService.FairnessSummary summary(@RequestParam(required = false) Integer windowDays) {
        return service.summary(windowDays);
    }
}
