package com.rynew.detl.bridge;

import java.time.Clock;
import com.hikvision.idatafusion.detl.mapper.EtlFlowMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityBridgeProperties.class)
@ConditionalOnProperty(prefix = "rynew.detl.identity-bridge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class IdentityBridgeAutoConfiguration {
    @Bean public FilterRegistrationBean<IdentityBridgeFilter> rynewDetlIdentityBridge(IdentityBridgeProperties properties, EtlFlowMapper flowMapper) {
        byte[] key = null;
        try {
            key = PrivateFiles.read(properties.getSecretFile(), 64);
            IdentityMappings mappings = new IdentityMappings(PrivateFiles.read(properties.getMappingFile(), 1024 * 1024));
            TicketVerifier verifier = new TicketVerifier(key, PrivateFiles.replayDirectory(properties.getReplayDirectory()), Clock.systemUTC());
            IdentityBridgeFilter filter = new IdentityBridgeFilter(verifier, mappings, new NativeSessionIssuer(properties.getSessionSeconds()), new NativeFlowAuthorizer(flowMapper));
            FilterRegistrationBean<IdentityBridgeFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setName("rynewDetlIdentityBridge"); registration.addUrlPatterns(IdentityBridgeFilter.SERVLET_PATH, IdentityBridgeFilter.AUTHORIZE_PATH);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50); registration.setAsyncSupported(false);
            return registration;
        } catch (Exception invalid) { throw new IllegalStateException("RYNEW identity bridge private configuration is invalid"); }
        finally { if (key != null) java.util.Arrays.fill(key, (byte)0); }
    }
}
