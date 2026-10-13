package com.hm.manage.controller;

import com.hm.common.core.domain.AjaxResult;
import com.hm.common.utils.SecurityUtils;
import com.hm.manage.service.governance.DataGovernanceHdiGateway;
import com.hm.manage.service.governance.DataGovernanceHdiSessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "data-governance-hdi-core", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DataGovernanceHdiController {
    private final DataGovernanceHdiSessions sessions;
    public DataGovernanceHdiController(DataGovernanceHdiSessions sessions) { this.sessions = sessions; }

    public ResponseEntity<AjaxResult> session(HttpServletRequest request, HttpServletResponse response) {
        return session(request, response, null);
    }

    @PostMapping("/governance/hdi/session")
    @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public ResponseEntity<AjaxResult> session(HttpServletRequest request, HttpServletResponse response,
            @RequestBody(required = false) Map<String, Object> options) {
        DataGovernanceHdiGateway.noCache(response);
        try {
            if (options != null && (options.keySet().stream().anyMatch(key -> !"renew".equals(key))
                    || (options.containsKey("renew") && !(options.get("renew") instanceof Boolean))))
                throw new DataGovernanceHdiSessions.Failure(400, "会话请求仅支持布尔值 renew");
            boolean renew = options != null && Boolean.TRUE.equals(options.get("renew"));
            var nativeSession = sessions.create(SecurityUtils.getLoginUser(), request, renew);
            Map<String, Object> data = new LinkedHashMap<>(nativeSession.context());
            data.put("frameUrl", nativeSession.frameUrl());
            String cookie = ResponseCookie.from(DataGovernanceHdiSessions.COOKIE, nativeSession.opaque())
                .httpOnly(true).secure(sessions.secureCookie()).sameSite("Strict").path("/detl").build().toString();
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie).body(AjaxResult.success(data));
        } catch (DataGovernanceHdiSessions.Failure error) {
            return ResponseEntity.status(error.status()).body(AjaxResult.error(error.status(), error.getMessage()));
        }
    }
}
