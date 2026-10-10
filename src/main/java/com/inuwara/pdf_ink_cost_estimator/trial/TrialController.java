package com.inuwara.pdf_ink_cost_estimator.trial;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrialController {

    private final TrialService trial;

    public TrialController(TrialService trial) {
        this.trial = trial;
    }

    @GetMapping("/api/trial")
    public TrialService.Status status() {
        return trial.check();
    }
}