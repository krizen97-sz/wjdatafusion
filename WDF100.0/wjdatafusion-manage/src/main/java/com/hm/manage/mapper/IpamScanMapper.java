package com.hm.manage.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.IpamScanJob;
import com.hm.manage.domain.IpamScanResult;

public interface IpamScanMapper
{
    int insertScanJob(IpamScanJob scanJob);

    IpamScanJob selectScanJobById(Long scanId);

    IpamScanJob selectLatestNetworkScanJob(Long networkId);

    int tryAcquireScanLock(@Param("ownerToken") String ownerToken,
                           @Param("now") Date now,
                           @Param("leaseUntil") Date leaseUntil);

    int bindScanLock(@Param("ownerToken") String ownerToken,
                     @Param("scanId") Long scanId,
                     @Param("now") Date now,
                     @Param("leaseUntil") Date leaseUntil);

    int renewScanLock(@Param("ownerToken") String ownerToken,
                      @Param("scanId") Long scanId,
                      @Param("now") Date now,
                      @Param("leaseUntil") Date leaseUntil);

    int releaseScanLock(@Param("ownerToken") String ownerToken,
                        @Param("now") Date now);

    int clearExpiredScanLock(@Param("now") Date now);

    int markInterruptedJobsFailed(@Param("staleBefore") Date staleBefore,
                                  @Param("finishedTime") Date finishedTime,
                                  @Param("errorMessage") String errorMessage);

    int markScanJobRunning(@Param("scanId") Long scanId,
                           @Param("totalCount") Long totalCount,
                           @Param("startedTime") Date startedTime);

    int updateScanJobProgress(@Param("scanId") Long scanId,
                              @Param("completedCount") Long completedCount,
                              @Param("onlineCount") Long onlineCount,
                              @Param("offlineCount") Long offlineCount,
                              @Param("errorCount") Long errorCount,
                              @Param("updateTime") Date updateTime);

    int finishScanJob(@Param("scanId") Long scanId,
                      @Param("scanStatus") String scanStatus,
                      @Param("completedCount") Long completedCount,
                      @Param("onlineCount") Long onlineCount,
                      @Param("offlineCount") Long offlineCount,
                      @Param("errorCount") Long errorCount,
                      @Param("finishedTime") Date finishedTime,
                      @Param("errorMessage") String errorMessage);

    int batchUpsertScanResults(@Param("results") List<IpamScanResult> results);

    List<IpamScanResult> selectResultsBySegmentIdAndRange(@Param("segmentId") Long segmentId,
                                                          @Param("startValue") Long startValue,
                                                          @Param("endValue") Long endValue);
}
