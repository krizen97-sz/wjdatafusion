package com.rynew.detl.bridge;

import com.hikvision.idatafusion.detl.dto.user.UserRole;
import com.hikvision.idatafusion.detl.utils.session.HttpUserSessionInfo;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Calls the original vendor session API; never alters license or workspace authorization code. */
final class NativeSessionIssuer {
    private final int sessionSeconds;
    NativeSessionIssuer(int sessionSeconds) {
        if (sessionSeconds < 60 || sessionSeconds > 3600) throw new IllegalArgumentException("Session duration is outside its bound");
        this.sessionSeconds = sessionSeconds;
    }
    void establish(IdentityMappings.Identity identity, HttpServletRequest request, HttpServletResponse response) {
        UserRole role = new UserRole();
        role.setUserId(identity.userId); role.setUserName(identity.userName); role.setAccountName(identity.accountName);
        role.setRealUserName(identity.realUserName); role.setUserRoleType(identity.roleType); role.setTenantCode(identity.tenantCode);
        role.setWorkspace(identity.workspaceId); role.setLanguage(identity.language); role.setUserIp(request.getRemoteAddr());
        RequestAttributes previous = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes bound = new ServletRequestAttributes(request, response);
        try {
            RequestContextHolder.setRequestAttributes(bound, false);
            HttpSession existing = request.getSession(false);
            if (existing != null) existing.invalidate();
            HttpUserSessionInfo.storeSessionLoginUserRole(role, identity.projectCode, identity.tenantCode);
            request.getSession(false).setMaxInactiveInterval(sessionSeconds);
        } catch (RuntimeException failure) {
            HttpSession partial = request.getSession(false); if (partial != null) partial.invalidate();
            throw failure;
        } finally {
            try { bound.requestCompleted(); }
            finally { if (previous == null) RequestContextHolder.resetRequestAttributes(); else RequestContextHolder.setRequestAttributes(previous, false); }
        }
    }
}
