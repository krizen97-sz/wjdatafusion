package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.config.IpamScanProperties;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.IpamScanJob;
import com.hm.manage.domain.IpamScanResult;
import com.hm.manage.domain.IpamSegment;
import com.hm.manage.mapper.IpamMapper;
import com.hm.manage.mapper.IpamScanMapper;
import com.hm.manage.service.IIpamScanService;
import com.hm.manage.util.IpamAddressUtils;
import com.hm.manage.util.IpamPingProbe;
import com.hm.manage.util.IpamPingProbe.ProbeResult;
import com.hm.manage.util.IpamScanLeaseHeartbeat;

@Service
public class IpamScanServiceImpl implements IIpamScanService
{
    private static final Logger log = LoggerFactory.getLogger(IpamScanServiceImpl.class);

    private static final String STATUS_NORMAL = "0";
    private static final String SCOPE_NETWORK = "NETWORK";
    private static final String SCOPE_ALL = "ALL";
    private static final String TRIGGER_MANUAL = "MANUAL";
    private static final String TRIGGER_SCHEDULED = "SCHEDULED";
    private static final String JOB_QUEUED = "QUEUED";
    private static final String JOB_RUNNING = "RUNNING";
    private static final String JOB_COMPLETED = "COMPLETED";
    private static final String JOB_PARTIAL = "PARTIAL";
    private static final String JOB_FAILED = "FAILED";
    private static final int SCAN_JOB_QUEUE_CAPACITY = 4;

    @Autowired
    private IpamMapper ipamMapper;

    @Autowired
    private IpamScanMapper ipamScanMapper;

    @Autowired
    private IpamScanProperties scanProperties;

    private final Object jobCreationMonitor = new Object();
    private final IpamPingProbe pingProbe = new IpamPingProbe();
    private final ExecutorService scanJobExecutor = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(SCAN_JOB_QUEUE_CAPACITY),
        namedThreadFactory("ipam-scan-job-"),
        new ThreadPoolExecutor.AbortPolicy()
    );

    @PostConstruct
    public void markInterruptedJobs()
    {
        try
        {
            Date now = DateUtils.getNowDate();
            Date staleBefore = new Date(now.getTime() - scanProperties.getLeaseSeconds() * 1000L);
            ipamScanMapper.clearExpiredScanLock(now);
            int interruptedJobs = ipamScanMapper.markInterruptedJobsFailed(
                staleBefore, now, "扫描任务租约已过期，已终止，请重新扫描");
            if (interruptedJobs > 0)
            {
                log.warn("服务启动时终止了{}个未完成的IPAM扫描任务", interruptedJobs);
            }
        }
        catch (Exception error)
        {
            log.warn("IPAM扫描任务状态初始化失败，请确认v3.9.7数据库脚本已执行：{}", error.getMessage());
        }
    }

    @PreDestroy
    public void shutdownExecutors()
    {
        scanJobExecutor.shutdownNow();
    }

    @Override
    public IpamScanJob startNetworkScan(Long networkId)
    {
        if (networkId == null)
        {
            throw new ServiceException("网段ID不能为空");
        }
        IpamNetwork network = requireEnabledNetwork(networkId);
        IpamScanJob scanJob = null;
        synchronized (jobCreationMonitor)
        {
            String lockToken = acquireScanLock();
            try
            {
                scanJob = createScanJob(
                    SCOPE_NETWORK, TRIGGER_MANUAL, network.getNetworkId(), network.getNetworkName(), currentUsername());
                bindScanLock(lockToken, scanJob.getScanId());
                submitManualJob(scanJob.getScanId(), List.of(network), lockToken);
            }
            catch (RuntimeException error)
            {
                if (scanJob != null)
                {
                    failQueuedJob(scanJob.getScanId(), "扫描任务创建或入队失败");
                }
                releaseScanLock(lockToken);
                throw error;
            }
        }
        if (scanJob == null)
        {
            throw new ServiceException("扫描任务创建失败");
        }
        return requireScanJob(scanJob.getScanId());
    }

    @Override
    public IpamScanJob selectScanJobById(Long scanId)
    {
        return requireScanJob(scanId);
    }

    @Override
    public IpamScanJob selectLatestNetworkScanJob(Long networkId)
    {
        if (networkId == null)
        {
            throw new ServiceException("网段ID不能为空");
        }
        return ipamScanMapper.selectLatestNetworkScanJob(networkId);
    }

    @Override
    public IpamScanJob scanAllNetworks()
    {
        List<IpamNetwork> networks = selectEnabledNetworks();
        if (networks.isEmpty())
        {
            throw new ServiceException("没有可扫描的启用网段");
        }

        IpamScanJob scanJob = null;
        Future<?> future;
        synchronized (jobCreationMonitor)
        {
            String lockToken = acquireScanLock();
            try
            {
                scanJob = createScanJob(SCOPE_ALL, TRIGGER_SCHEDULED, null, "全部启用网段", "quartz");
                bindScanLock(lockToken, scanJob.getScanId());
                Long scanId = scanJob.getScanId();
                future = scanJobExecutor.submit(() -> executeJob(scanId, networks, lockToken));
            }
            catch (RuntimeException error)
            {
                if (scanJob != null)
                {
                    failQueuedJob(scanJob.getScanId(), "扫描任务创建或入队失败");
                }
                releaseScanLock(lockToken);
                throw new ServiceException("扫描任务创建失败：" + limitErrorMessage(error.getMessage()));
            }
        }
        if (scanJob == null)
        {
            throw new ServiceException("扫描任务创建失败");
        }

        try
        {
            future.get();
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new ServiceException("全域扫描等待过程被中断");
        }
        catch (ExecutionException executionError)
        {
            Throwable cause = executionError.getCause();
            throw new ServiceException(cause == null ? "全域扫描执行失败" : cause.getMessage());
        }
        return requireScanJob(scanJob.getScanId());
    }

    private void submitManualJob(Long scanId, List<IpamNetwork> networks, String lockToken)
    {
        try
        {
            scanJobExecutor.submit(() -> executeJob(scanId, networks, lockToken));
        }
        catch (RuntimeException error)
        {
            throw new ServiceException("扫描任务队列已满，请稍后重试");
        }
    }

    private void executeJob(Long scanId, List<IpamNetwork> networks, String lockToken)
    {
        ScanCounters counters = new ScanCounters();
        ExecutorService probeExecutor = null;
        try
        {
            List<SegmentTarget> targets = collectTargets(networks);
            long totalCount = targets.stream().mapToLong(SegmentTarget::addressCount).sum();
            if (totalCount <= 0)
            {
                throw new ServiceException("网段内没有可检测的IP地址");
            }
            if (ipamScanMapper.markScanJobRunning(scanId, totalCount, DateUtils.getNowDate()) != 1)
            {
                throw new ServiceException("扫描任务状态已变化，无法开始执行");
            }
            renewScanLock(lockToken, scanId);
            IpamScanLeaseHeartbeat leaseHeartbeat = new IpamScanLeaseHeartbeat(scanProperties.getLeaseSeconds());

            probeExecutor = new ThreadPoolExecutor(
                scanProperties.getConcurrency(),
                scanProperties.getConcurrency(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(scanProperties.getConcurrency()),
                namedThreadFactory("ipam-ping-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
            );
            List<IpamScanResult> pendingResults = new ArrayList<>(scanProperties.getBatchSize());
            for (SegmentTarget target : targets)
            {
                scanSegment(scanId, target, probeExecutor, counters, pendingResults, lockToken, leaseHeartbeat);
            }
            flushResults(scanId, counters, pendingResults, lockToken, leaseHeartbeat);

            if (counters.completedCount > 0 && counters.errorCount == counters.completedCount)
            {
                throw new ServiceException("全部IP探针执行异常，请检查服务器Ping命令和系统权限");
            }
            String finalStatus = counters.errorCount > 0 ? JOB_PARTIAL : JOB_COMPLETED;
            finishJob(scanId, finalStatus, counters, null);
            log.info("IPAM扫描完成，任务ID：{}，总数：{}，在线：{}，离线：{}，异常：{}",
                scanId, counters.completedCount, counters.onlineCount,
                counters.offlineCount, counters.errorCount);
        }
        catch (Exception error)
        {
            String message = StringUtils.defaultIfBlank(error.getMessage(), "扫描执行失败");
            message = limitErrorMessage(message);
            finishJob(scanId, JOB_FAILED, counters, message);
            log.error("IPAM扫描失败，任务ID：{}，原因：{}", scanId, message, error);
            throw error instanceof RuntimeException
                ? (RuntimeException) error
                : new ServiceException(message);
        }
        finally
        {
            if (probeExecutor != null)
            {
                probeExecutor.shutdownNow();
            }
            releaseScanLock(lockToken);
        }
    }

    private void scanSegment(
        Long scanId,
        SegmentTarget target,
        ExecutorService probeExecutor,
        ScanCounters counters,
        List<IpamScanResult> pendingResults,
        String lockToken,
        IpamScanLeaseHeartbeat leaseHeartbeat) throws InterruptedException, ExecutionException
    {
        CompletionService<IpamScanResult> completionService = new ExecutorCompletionService<>(probeExecutor);
        long nextIpValue = target.startValue() + 1L;
        int inFlight = 0;
        while (nextIpValue < target.endValue() || inFlight > 0)
        {
            while (nextIpValue < target.endValue() && inFlight < scanProperties.getConcurrency())
            {
                final long ipValue = nextIpValue++;
                completionService.submit(() -> probeAddress(scanId, target, ipValue));
                inFlight++;
                pauseBetweenSubmissions();
                renewScanLockIfDue(lockToken, scanId, leaseHeartbeat);
            }

            IpamScanResult result = completionService.take().get();
            renewScanLockIfDue(lockToken, scanId, leaseHeartbeat);
            inFlight--;
            counters.accept(result);
            pendingResults.add(result);
            if (pendingResults.size() >= scanProperties.getBatchSize())
            {
                flushResults(scanId, counters, pendingResults, lockToken, leaseHeartbeat);
            }
        }
    }

    private IpamScanResult probeAddress(Long scanId, SegmentTarget target, long ipValue)
    {
        String ipAddress = IpamAddressUtils.longToIp(ipValue);
        ProbeResult probe = pingProbe.probe(ipAddress, scanProperties.getTimeoutMs());
        Date scanTime = DateUtils.getNowDate();

        IpamScanResult result = new IpamScanResult();
        result.setScanId(scanId);
        result.setNetworkId(target.networkId());
        result.setSegmentId(target.segmentId());
        result.setIpAddress(ipAddress);
        result.setIpValue(ipValue);
        result.setConnectivityStatus(probe.getStatus());
        result.setResponseTimeMs(probe.getResponseTimeMs());
        result.setLastScanTime(scanTime);
        result.setErrorMessage(limitErrorMessage(probe.getErrorMessage()));
        if (IpamPingProbe.STATUS_ONLINE.equals(probe.getStatus()))
        {
            result.setLastOnlineTime(scanTime);
        }
        else if (IpamPingProbe.STATUS_OFFLINE.equals(probe.getStatus()))
        {
            result.setLastOfflineTime(scanTime);
        }
        return result;
    }

    private void flushResults(
        Long scanId,
        ScanCounters counters,
        List<IpamScanResult> pendingResults,
        String lockToken,
        IpamScanLeaseHeartbeat leaseHeartbeat)
    {
        if (pendingResults.isEmpty())
        {
            return;
        }
        ipamScanMapper.batchUpsertScanResults(pendingResults);
        pendingResults.clear();
        ipamScanMapper.updateScanJobProgress(
            scanId,
            counters.completedCount,
            counters.onlineCount,
            counters.offlineCount,
            counters.errorCount,
            DateUtils.getNowDate()
        );
        renewScanLock(lockToken, scanId);
        leaseHeartbeat.markRenewed();
    }

    private void renewScanLockIfDue(String lockToken, Long scanId, IpamScanLeaseHeartbeat leaseHeartbeat)
    {
        if (!leaseHeartbeat.isRenewalDue())
        {
            return;
        }
        renewScanLock(lockToken, scanId);
        leaseHeartbeat.markRenewed();
    }

    private List<SegmentTarget> collectTargets(List<IpamNetwork> networks)
    {
        List<SegmentTarget> targets = new ArrayList<>();
        Map<Long, Long> addressCountsByNetwork = new HashMap<>();
        for (IpamNetwork network : networks)
        {
            IpamSegment query = new IpamSegment();
            query.setNetworkId(network.getNetworkId());
            query.setStatus(STATUS_NORMAL);
            List<IpamSegment> segments = ipamMapper.selectSegmentList(query);
            if (segments.isEmpty())
            {
                throw new ServiceException("网段“" + network.getNetworkName() + "”没有可扫描的地址池");
            }
            for (IpamSegment segment : segments)
            {
                long startValue = IpamAddressUtils.ipToLong(segment.getStartIp());
                long endValue = IpamAddressUtils.ipToLong(segment.getEndIp());
                long addressCount = Math.max(endValue - startValue - 1L, 0L);
                long networkAddressCount = addressCountsByNetwork.merge(
                    network.getNetworkId(), addressCount, Long::sum);
                if (networkAddressCount > scanProperties.getMaxAddressesPerNetwork())
                {
                    throw new ServiceException(
                        "网段“" + network.getNetworkName() + "”包含" + networkAddressCount
                            + "个待检测地址，超过安全上限" + scanProperties.getMaxAddressesPerNetwork());
                }
                targets.add(new SegmentTarget(
                    network.getNetworkId(), segment.getSegmentId(), startValue, endValue, addressCount));
            }
        }
        return targets;
    }

    private void pauseBetweenSubmissions() throws InterruptedException
    {
        int intervalMs = scanProperties.getIntervalMs();
        if (intervalMs > 0)
        {
            Thread.sleep(intervalMs);
        }
    }

    private String acquireScanLock()
    {
        String ownerToken = UUID.randomUUID().toString();
        Date now = DateUtils.getNowDate();
        if (ipamScanMapper.tryAcquireScanLock(ownerToken, now, nextLeaseTime(now)) != 1)
        {
            throw new ServiceException("已有扫描任务正在排队或执行，请稍后再试");
        }
        return ownerToken;
    }

    private void bindScanLock(String ownerToken, Long scanId)
    {
        Date now = DateUtils.getNowDate();
        if (ipamScanMapper.bindScanLock(ownerToken, scanId, now, nextLeaseTime(now)) != 1)
        {
            throw new ServiceException("扫描任务锁已失效，无法创建任务");
        }
    }

    private void renewScanLock(String ownerToken, Long scanId)
    {
        Date now = DateUtils.getNowDate();
        if (ipamScanMapper.renewScanLock(ownerToken, scanId, now, nextLeaseTime(now)) != 1)
        {
            throw new ServiceException("扫描任务锁已失效，任务已停止");
        }
    }

    private void releaseScanLock(String ownerToken)
    {
        if (StringUtils.isBlank(ownerToken))
        {
            return;
        }
        try
        {
            ipamScanMapper.releaseScanLock(ownerToken, DateUtils.getNowDate());
        }
        catch (Exception error)
        {
            log.warn("IPAM扫描锁释放失败：{}", error.getMessage());
        }
    }

    private Date nextLeaseTime(Date now)
    {
        return new Date(now.getTime() + scanProperties.getLeaseSeconds() * 1000L);
    }

    private IpamScanJob createScanJob(
        String scanScope,
        String triggerType,
        Long networkId,
        String networkName,
        String operator)
    {
        Date now = DateUtils.getNowDate();
        IpamScanJob scanJob = new IpamScanJob();
        scanJob.setScanScope(scanScope);
        scanJob.setTriggerType(triggerType);
        scanJob.setNetworkId(networkId);
        scanJob.setNetworkName(networkName);
        scanJob.setScanStatus(JOB_QUEUED);
        scanJob.setTimeoutMs(scanProperties.getTimeoutMs());
        scanJob.setIntervalMs(scanProperties.getIntervalMs());
        scanJob.setConcurrencyCount(scanProperties.getConcurrency());
        scanJob.setCreateBy(operator);
        scanJob.setCreateTime(now);
        scanJob.setUpdateBy(operator);
        scanJob.setUpdateTime(now);
        ipamScanMapper.insertScanJob(scanJob);
        if (scanJob.getScanId() == null)
        {
            throw new ServiceException("扫描任务创建失败");
        }
        return scanJob;
    }

    private void failQueuedJob(Long scanId, String message)
    {
        ScanCounters counters = new ScanCounters();
        finishJob(scanId, JOB_FAILED, counters, message);
    }

    private void finishJob(Long scanId, String status, ScanCounters counters, String errorMessage)
    {
        try
        {
            ipamScanMapper.finishScanJob(
                scanId,
                status,
                counters.completedCount,
                counters.onlineCount,
                counters.offlineCount,
                counters.errorCount,
                DateUtils.getNowDate(),
                limitErrorMessage(errorMessage)
            );
        }
        catch (Exception finishError)
        {
            log.error("IPAM扫描任务状态写入失败，任务ID：{}", scanId, finishError);
        }
    }

    private IpamNetwork requireEnabledNetwork(Long networkId)
    {
        IpamNetwork network = ipamMapper.selectNetworkById(networkId);
        if (network == null)
        {
            throw new ServiceException("网段不存在");
        }
        if (!STATUS_NORMAL.equals(network.getStatus()))
        {
            throw new ServiceException("停用网段不能执行扫描");
        }
        return network;
    }

    private List<IpamNetwork> selectEnabledNetworks()
    {
        IpamNetwork query = new IpamNetwork();
        query.setStatus(STATUS_NORMAL);
        return ipamMapper.selectNetworkList(query);
    }

    private IpamScanJob requireScanJob(Long scanId)
    {
        if (scanId == null)
        {
            throw new ServiceException("扫描任务ID不能为空");
        }
        IpamScanJob scanJob = ipamScanMapper.selectScanJobById(scanId);
        if (scanJob == null)
        {
            throw new ServiceException("扫描任务不存在");
        }
        return scanJob;
    }

    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception ignored)
        {
            return "system";
        }
    }

    private String limitErrorMessage(String message)
    {
        if (StringUtils.isBlank(message))
        {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static ThreadFactory namedThreadFactory(String prefix)
    {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable ->
        {
            Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record SegmentTarget(
        Long networkId,
        Long segmentId,
        long startValue,
        long endValue,
        long addressCount)
    {
    }

    private static class ScanCounters
    {
        private long completedCount;
        private long onlineCount;
        private long offlineCount;
        private long errorCount;

        private void accept(IpamScanResult result)
        {
            completedCount++;
            if (IpamPingProbe.STATUS_ONLINE.equals(result.getConnectivityStatus()))
            {
                onlineCount++;
            }
            else if (IpamPingProbe.STATUS_OFFLINE.equals(result.getConnectivityStatus()))
            {
                offlineCount++;
            }
            else
            {
                errorCount++;
            }
        }
    }
}
