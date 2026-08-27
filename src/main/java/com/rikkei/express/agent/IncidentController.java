package com.rikkei.express.agent;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoint kích hoạt Autonomous Incident Responder. */
@RestController
@RequestMapping("/api/v1/logistics")
public class IncidentController {

    private final AutonomousLogisticsAgentService agentService;

    public IncidentController(AutonomousLogisticsAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/incidents/process")
    public IncidentReport processIncident(@RequestBody Map<String, String> request) {
        String postOfficeCode = request.getOrDefault("postOfficeCode", "SG-02");
        return agentService.processIncident(postOfficeCode);
    }
}