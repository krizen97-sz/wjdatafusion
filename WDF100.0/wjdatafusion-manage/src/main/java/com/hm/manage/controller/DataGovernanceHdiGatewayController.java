package com.hm.manage.controller;

import com.hm.manage.service.governance.DataGovernanceHdiGateway;
import com.hm.manage.service.governance.DataGovernanceHdiSessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "data-governance-hdi", name = "enabled", havingValue = "true")
public class DataGovernanceHdiGatewayController {
    private final DataGovernanceHdiGateway gateway;
    public DataGovernanceHdiGatewayController(DataGovernanceHdiGateway gateway) { this.gateway = gateway; }
    @RequestMapping({"/detl", "/detl/**"})
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try { gateway.handle(request, response); }
        catch (DataGovernanceHdiSessions.Failure error) { DataGovernanceHdiGateway.failure(response, error); }
    }
}
