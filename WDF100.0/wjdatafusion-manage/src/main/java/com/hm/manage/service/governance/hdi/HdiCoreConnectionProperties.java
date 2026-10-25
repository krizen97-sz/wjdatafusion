package com.hm.manage.service.governance.hdi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Deployment-owned network policy, never accepted from a diagram or connection form. */
@Component
@ConfigurationProperties(prefix = "data-governance-hdi-core.connections")
public class HdiCoreConnectionProperties {
    private List<String> allowedEndpoints = new ArrayList<>();
    private Map<String, String> ftpPassiveRanges = new LinkedHashMap<>();
    private Map<String, List<String>> kafkaBrokers = new LinkedHashMap<>();
    public List<String> getAllowedEndpoints() { return List.copyOf(allowedEndpoints); }
    public void setAllowedEndpoints(List<String> value) { allowedEndpoints = new ArrayList<>(value); }
    public Map<String, String> getFtpPassiveRanges() { return Map.copyOf(ftpPassiveRanges); }
    public void setFtpPassiveRanges(Map<String, String> value) { ftpPassiveRanges = new LinkedHashMap<>(value); }
    public Map<String, List<String>> getKafkaBrokers() { return Map.copyOf(kafkaBrokers); }
    public void setKafkaBrokers(Map<String, List<String>> value) { kafkaBrokers = new LinkedHashMap<>(value); }
}
