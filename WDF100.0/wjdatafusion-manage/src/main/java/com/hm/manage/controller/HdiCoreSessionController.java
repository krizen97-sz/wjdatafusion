package com.hm.manage.controller;

import com.hm.common.core.domain.AjaxResult;
import com.hm.common.utils.SecurityUtils;
import com.hm.manage.service.governance.hdi.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix = "data-governance-hdi-core", name = "enabled", havingValue = "true")
public class HdiCoreSessionController {
    private final HdiCoreSessions sessions;
    public HdiCoreSessionController(HdiCoreSessions sessions) { this.sessions = sessions; }
    @PostMapping("/governance/hdi/session")
    @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public ResponseEntity<AjaxResult> create(HttpServletRequest request, HttpServletResponse response, @RequestBody(required = false) Map<String, Object> options) {
        HdiProtocol.noCache(response);
        try {
            if (options != null && (options.keySet().stream().anyMatch(k -> !k.equals("renew")) || options.containsKey("renew") && !(options.get("renew") instanceof Boolean))) throw new HdiTaskFault(400, "会话请求仅支持 renew 布尔值");
            var user = SecurityUtils.getLoginUser(); var session = sessions.create(user, request, options != null && Boolean.TRUE.equals(options.get("renew")));
            String cookie = ResponseCookie.from(HdiCoreSessions.COOKIE, session.opaque).httpOnly(true).secure(sessions.secureCookie()).sameSite("Strict").path("/detl").build().toString();
            return ResponseEntity.ok().header("Set-Cookie", cookie).body(AjaxResult.success(sessions.context(session, user)));
        } catch (HdiTaskFault failure) { return ResponseEntity.status(failure.status()).body(AjaxResult.error(failure.status(), failure.getMessage())); }
    }
}
