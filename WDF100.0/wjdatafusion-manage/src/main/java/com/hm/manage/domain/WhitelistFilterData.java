package com.hm.manage.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class WhitelistFilterData extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    @Excel(name = "车牌号")
    private String plateNo;
    @Excel(name = "告警类型")
    private String alarmType;
    @Excel(name = "通道名称")
    private String channelName;
    @Excel(name = "相机名称")
    private String cameraName;
    @Excel(name = "设备名称")
    private String deviceName;
    @Excel(name = "车辆类型")
    private String vehicleType;
    @Excel(name = "车牌颜色")
    private String plateColor;
    private Integer channelId;
    private String dataType;
    private String eventType;
    private String eventDescription;
    private String ipAddress;
    private Integer portNo;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "发送时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date sendTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "接收时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date recvTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "过车时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date passTime;
    private String cameraAddress;
    private String directionIndex;
    private Long crossingId;
    private Integer laneNo;
    private String passId;
    private String eventUuid;
    private String taskId;
    private String targetPicUrl;
    private String rawJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlateNo() { return plateNo; }
    public void setPlateNo(String plateNo) { this.plateNo = plateNo; }
    public String getAlarmType() { return alarmType; }
    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public String getCameraName() { return cameraName; }
    public void setCameraName(String cameraName) { this.cameraName = cameraName; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getPlateColor() { return plateColor; }
    public void setPlateColor(String plateColor) { this.plateColor = plateColor; }
    public Integer getChannelId() { return channelId; }
    public void setChannelId(Integer channelId) { this.channelId = channelId; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Integer getPortNo() { return portNo; }
    public void setPortNo(Integer portNo) { this.portNo = portNo; }
    public Date getSendTime() { return sendTime; }
    public void setSendTime(Date sendTime) { this.sendTime = sendTime; }
    public Date getRecvTime() { return recvTime; }
    public void setRecvTime(Date recvTime) { this.recvTime = recvTime; }
    public Date getPassTime() { return passTime; }
    public void setPassTime(Date passTime) { this.passTime = passTime; }
    public String getCameraAddress() { return cameraAddress; }
    public void setCameraAddress(String cameraAddress) { this.cameraAddress = cameraAddress; }
    public String getDirectionIndex() { return directionIndex; }
    public void setDirectionIndex(String directionIndex) { this.directionIndex = directionIndex; }
    public Long getCrossingId() { return crossingId; }
    public void setCrossingId(Long crossingId) { this.crossingId = crossingId; }
    public Integer getLaneNo() { return laneNo; }
    public void setLaneNo(Integer laneNo) { this.laneNo = laneNo; }
    public String getPassId() { return passId; }
    public void setPassId(String passId) { this.passId = passId; }
    public String getEventUuid() { return eventUuid; }
    public void setEventUuid(String eventUuid) { this.eventUuid = eventUuid; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTargetPicUrl() { return targetPicUrl; }
    public void setTargetPicUrl(String targetPicUrl) { this.targetPicUrl = targetPicUrl; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
}
