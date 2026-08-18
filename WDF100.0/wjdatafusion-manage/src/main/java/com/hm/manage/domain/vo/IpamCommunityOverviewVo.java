package com.hm.manage.domain.vo;

public class IpamCommunityOverviewVo
{
    private String communityName;
    private Long addressCount;
    private Long deviceCount;
    private Long networkCount;
    private Long allocatedCount;
    private Long issuedCount;
    private String firstIp;
    private String lastIp;
    private String networkNameSummary;
    private String policeStationSummary;
    private String internalIpSummary;
    private String targetTypeSummary;
    private String manufacturerSummary;
    private String accessUnitSummary;
    private String ownerSummary;
    private String mappingSummary;

    public String getCommunityName() { return communityName; }
    public void setCommunityName(String communityName) { this.communityName = communityName; }
    public Long getAddressCount() { return addressCount; }
    public void setAddressCount(Long addressCount) { this.addressCount = addressCount; }
    public Long getDeviceCount() { return deviceCount; }
    public void setDeviceCount(Long deviceCount) { this.deviceCount = deviceCount; }
    public Long getNetworkCount() { return networkCount; }
    public void setNetworkCount(Long networkCount) { this.networkCount = networkCount; }
    public Long getAllocatedCount() { return allocatedCount; }
    public void setAllocatedCount(Long allocatedCount) { this.allocatedCount = allocatedCount; }
    public Long getIssuedCount() { return issuedCount; }
    public void setIssuedCount(Long issuedCount) { this.issuedCount = issuedCount; }
    public String getFirstIp() { return firstIp; }
    public void setFirstIp(String firstIp) { this.firstIp = firstIp; }
    public String getLastIp() { return lastIp; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }
    public String getNetworkNameSummary() { return networkNameSummary; }
    public void setNetworkNameSummary(String networkNameSummary) { this.networkNameSummary = networkNameSummary; }
    public String getPoliceStationSummary() { return policeStationSummary; }
    public void setPoliceStationSummary(String policeStationSummary) { this.policeStationSummary = policeStationSummary; }
    public String getInternalIpSummary() { return internalIpSummary; }
    public void setInternalIpSummary(String internalIpSummary) { this.internalIpSummary = internalIpSummary; }
    public String getTargetTypeSummary() { return targetTypeSummary; }
    public void setTargetTypeSummary(String targetTypeSummary) { this.targetTypeSummary = targetTypeSummary; }
    public String getManufacturerSummary() { return manufacturerSummary; }
    public void setManufacturerSummary(String manufacturerSummary) { this.manufacturerSummary = manufacturerSummary; }
    public String getAccessUnitSummary() { return accessUnitSummary; }
    public void setAccessUnitSummary(String accessUnitSummary) { this.accessUnitSummary = accessUnitSummary; }
    public String getOwnerSummary() { return ownerSummary; }
    public void setOwnerSummary(String ownerSummary) { this.ownerSummary = ownerSummary; }
    public String getMappingSummary() { return mappingSummary; }
    public void setMappingSummary(String mappingSummary) { this.mappingSummary = mappingSummary; }
}
