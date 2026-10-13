package com.hm.manage.controller;

import com.hm.manage.service.governance.hdi.HdiCoreApi;
import com.hm.manage.service.governance.hdi.HdiProtocol;
import com.hm.manage.service.governance.hdi.HdiTaskFault;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "data-governance-hdi-core", name = "enabled", havingValue = "true")
public class DataGovernanceHdiCoreController {
    private final HdiCoreApi api;
    public DataGovernanceHdiCoreController(HdiCoreApi api) { this.api = api; }
    @RequestMapping({"/detl", "/detl/**"})
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try { api.handle(request, response); }
        catch (HdiTaskFault failure) { if (!response.isCommitted()) HdiProtocol.failure(response, failure); }
        catch (RuntimeException unavailable) {
            // Database/compiler exceptions can contain task data. Never reflect those into the original UI.
            if (!response.isCommitted()) HdiProtocol.failure(response, new HdiTaskFault(503, "任务服务暂不可用，请稍后重试"));
        }
    }
}
