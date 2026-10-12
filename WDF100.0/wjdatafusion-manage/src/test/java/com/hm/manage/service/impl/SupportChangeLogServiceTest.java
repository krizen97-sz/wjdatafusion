package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.manage.domain.SupportChangeLog;
import com.hm.manage.mapper.SupportChangeLogMapper;

class SupportChangeLogServiceTest
{
    private final SupportChangeLogMapper mapper = mock(SupportChangeLogMapper.class);
    private final SupportChangeLogServiceImpl service = new SupportChangeLogServiceImpl();

    SupportChangeLogServiceTest() { ReflectionTestUtils.setField(service, "changeLogMapper", mapper); }

    @AfterEach void cleanup()
    {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test void workerWithoutServletRequestStillRecordsNicknameAndUnknownIp()
    {
        RequestContextHolder.resetRequestAttributes();
        SysUser user = new SysUser(); user.setUserName("operator"); user.setNickName("Operator nickname");
        LoginUser principal = new LoginUser(1L, 1L, user, Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
        assertDoesNotThrow(() -> service.record(9L, "UPDATE", "SERVER_SCAN", 7L, "fixture", "scan synchronized"));
        var saved = ArgumentCaptor.forClass(SupportChangeLog.class);
        verify(mapper).insertSupportChangeLog(saved.capture());
        assertEquals("Operator nickname", saved.getValue().getOperatorName());
        assertEquals("unknown", saved.getValue().getOperatorIp());
    }

    @Test void webRequestRetainsExistingClientIpResolution()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "192.0.2.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        service.record(9L, "UPDATE", "SERVER", 7L, "fixture", "edited");
        verify(mapper).insertSupportChangeLog(argThat(row -> "192.0.2.10".equals(row.getOperatorIp())));
    }

    @Test void explicitOperatorIpIsNotReplaced()
    {
        SupportChangeLog record = new SupportChangeLog();
        record.setOperatorName("operator"); record.setOperatorIp("192.0.2.11");
        assertDoesNotThrow(() -> service.insertSupportChangeLog(record));
        assertEquals("192.0.2.11", record.getOperatorIp());
    }
}
