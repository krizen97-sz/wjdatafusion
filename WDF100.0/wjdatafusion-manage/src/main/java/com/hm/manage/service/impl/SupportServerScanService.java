package com.hm.manage.service.impl;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import jakarta.annotation.PreDestroy;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.bo.SupportServerScanBo;
import com.hm.manage.service.ISupportPlatformService;
import com.hm.manage.service.support.*;

@Service
public class SupportServerScanService
{
    private static final Set<String> RUNNING = Set.of("DISCOVERING", "SCANNING", "STOPPING");
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService coordinators = Executors.newFixedThreadPool(2, threads("server-scan-coordinator-"));
    private final ExecutorService workers = Executors.newFixedThreadPool(4, threads("server-scan-worker-"));
    private final SupportServerScanProbe probe;
    private final SupportServerLoginVerifier verifier;
    private final SupportServerLoginGuard guard;
    private final SupportServerScanCredentialService credentials;
    private final CredentialCryptoService crypto;
    private final SupportEquipmentWorkbookService workbook;
    private final SupportServerScanSyncService sync;
    private final ISupportPlatformService platforms;

    public SupportServerScanService(SupportServerScanProbe probe, SupportServerLoginVerifier verifier, SupportServerLoginGuard guard,
        SupportServerScanCredentialService credentials, CredentialCryptoService crypto, SupportEquipmentWorkbookService workbook,
        SupportServerScanSyncService sync, ISupportPlatformService platforms)
    {
        this.probe = probe; this.verifier = verifier; this.guard = guard; this.credentials = credentials;
        this.crypto = crypto; this.workbook = workbook; this.sync = sync; this.platforms = platforms;
    }

    public synchronized Map<String, Object> start(SupportServerScanBo.Start command, Long userId, String actor)
    {
        if (command == null || command.siteId() == null || command.platformId() == null) throw new ServiceException("请选择现场及新服务器所属子平台");
        SupportPlatform platform = platforms.selectSupportPlatformByPlatformId(command.platformId());
        if (platform == null || !command.siteId().equals(platform.getSiteId()) || !"SUB".equals(platform.getPlatformLevel()))
            throw new ServiceException("只能选择当前现场的子平台");
        int limit = command.attemptLimit() == null ? 1 : command.attemptLimit();
        if (limit < 1 || limit > 3) throw new ServiceException("每个地址最多尝试1至3组授权凭据");
        List<String> hosts = SupportServerScanTargets.expand(command.targets());
        List<Integer> ports = SupportServerScanTargets.ports(command.ports());
        List<Map<String, Object>> selected = credentials.selected(command.siteId(), command.credentialIds());
        prune();
        if (jobs.size() >= 50 || jobs.values().stream().filter(job -> RUNNING.contains(job.state)).count() >= 2)
            throw new ServiceException("扫描任务繁忙，请等待其他任务结束");
        if (jobs.values().stream().anyMatch(job -> job.siteId.equals(command.siteId()) && (RUNNING.contains(job.state) || "REVIEW".equals(job.state))))
            throw new ServiceException("该现场已有扫描任务，请先结束或取消");
        Map<String, Object> snapshot = workbook.snapshot(command.siteId());
        Job job = new Job(command.siteId(), command.platformId(), userId, actor, ports, selected, limit);
        Map<String, Map<String, Object>> existing = new HashMap<>();
        @SuppressWarnings("unchecked") List<Map<String, Object>> saved = (List<Map<String, Object>>) snapshot.get("rows");
        for (Map<String, Object> row : saved)
        {
            Set<String> addresses = new LinkedHashSet<>();
            addresses.add(SupportServerScanTargets.canonical(Objects.toString(row.get("ipAddress"), "")));
            if ("HARDWARE".equals(row.get("sourceType"))) addresses.add(SupportServerScanTargets.canonical(Objects.toString(row.get("manageIp"), "")));
            for (String address : addresses)
            {
                if (address.isEmpty()) continue;
                if (existing.put(address, row) != null && hosts.contains(address)) throw new ServiceException("当前现场同IP存在多条设备记录，请先处理或缩小扫描范围");
            }
        }
        for (String host : hosts) job.rows.put(host, new Target(host, existing.get(host)));
        jobs.put(job.id, job);
        execute(job, new ArrayList<>(job.rows.values()), target -> discover(job, target), "REVIEW", null);
        return job.view();
    }

    public Map<String, Object> status(String id, Long userId) { return owned(id, userId).view(); }
    public Map<String, Object> active(Long siteId, Long userId)
    {
        prune();
        return jobs.values().stream().filter(job -> job.userId.equals(userId) && job.siteId.equals(siteId))
            .max(Comparator.comparingLong(job -> job.created)).map(Job::view).orElse(null);
    }
    public Map<String, Object> cancel(String id, Long userId)
    {
        Job job = owned(id, userId);
        synchronized (job)
        {
            if (!RUNNING.contains(job.state) && !"REVIEW".equals(job.state)) return job.view();
            job.cancelled = true;
            job.state = RUNNING.contains(job.state) ? "STOPPING" : "CANCELLED";
            return job.view();
        }
    }

    public synchronized Map<String, Object> confirm(String id, Long userId, SupportServerScanBo.Confirm input)
    {
        Job job = owned(id, userId);
        if (input == null || !input.fingerprintsConfirmed() || input.targets() == null || input.targets().isEmpty() || input.targets().size() > 128)
            throw new ServiceException("请核对主机指纹并选择1至128台服务器");
        SecurityContext context = SecurityContextHolder.getContext();
        synchronized (job)
        {
            if (!"REVIEW".equals(job.state) || job.cancelled) throw new ServiceException("任务状态已变化，请刷新");
            if (jobs.values().stream().filter(item -> RUNNING.contains(item.state)).count() >= 2) throw new ServiceException("扫描任务繁忙，请稍后继续");
            List<Long> ids = job.candidates.stream().map(row -> ((Number) row.get("credentialId")).longValue()).toList();
            if (!sameCredentials(job.candidates, credentials.selected(job.siteId, ids))) throw new ServiceException("授权凭据池已变化，请重新发起扫描");
            Set<String> seen = new HashSet<>();
            List<Target> chosen = new ArrayList<>();
            for (var selection : input.targets())
            {
                if (selection == null || !seen.add(selection.address())) throw new ServiceException("目标重复或无效");
                Target target = job.rows.get(selection.address());
                if (target == null || !"READY".equals(target.state)) throw new ServiceException("目标不属于本次可核验的服务器");
                target.chosen = target.ports.stream().filter(port -> Objects.equals(port.port(), selection.port()) && "READY".equals(port.status())).findFirst()
                    .orElseThrow(() -> new ServiceException("所选端口没有已探测的SSH指纹"));
                if (target.original != null && (!Boolean.TRUE.equals(target.original.get("canEdit")) || !"0".equals(String.valueOf(target.original.get("status")))))
                    throw new ServiceException("已有服务器已停用或不可修改");
                chosen.add(target);
            }
            job.done = 0; job.total = chosen.size(); job.state = "SCANNING";
            for (Target target : job.rows.values()) target.state = seen.contains(target.address) ? "PENDING" : "SKIPPED";
            execute(job, chosen, target -> authenticate(job, target), "DONE", context);
            return job.view();
        }
    }

    private void discover(Job job, Target target)
    {
        List<SupportServerScanProbe.Port> result = new ArrayList<>();
        for (int port : job.ports)
        {
            if (job.cancelled || Thread.currentThread().isInterrupted()) return;
            result.add(probe.inspect(target.address, port));
        }
        synchronized (job)
        {
            target.ports = List.copyOf(result);
            target.state = result.stream().anyMatch(port -> "READY".equals(port.status())) ? "READY" : "NO_SSH";
            target.message = "READY".equals(target.state) ? "主机指纹待确认" : "没有可确认的SSH服务，未尝试密码";
            if (target.original != null && (!Boolean.TRUE.equals(target.original.get("canEdit")) || !"0".equals(String.valueOf(target.original.get("status")))))
            { target.state = "SKIPPED"; target.message = "已有服务器已停用或不可修改，跳过登录与同步"; }
            if (target.original != null && !"SERVER".equals(target.original.get("sourceType")))
            { target.state = "SKIPPED"; target.message = "已登记为硬件设备，避免重复新增为服务器"; }
        }
    }

    private void authenticate(Job job, Target target)
    {
        if (job.cancelled) return;
        try (SupportServerLoginGuard.Lease lease = guard.acquire(target.address))
        {
            for (int index = 0; index < Math.min(job.attemptLimit, job.candidates.size()); index++)
            {
                if (job.cancelled || Thread.currentThread().isInterrupted()) return;
                List<Long> ids = job.candidates.stream().map(row -> ((Number) row.get("credentialId")).longValue()).toList();
                if (!sameCredentials(job.candidates, credentials.selected(job.siteId, ids))) throw new ServiceException("授权凭据已变化，已停止使用旧凭据");
                Map<String, Object> candidate = job.candidates.get(index);
                boolean direct = "ROOT_DIRECT".equals(candidate.get("mode"));
                String root = crypto.decrypt(String.valueOf(candidate.get("rootCipher")));
                String hik = direct ? "" : crypto.decrypt(String.valueOf(candidate.get("hikCipher")));
                if (root == null || root.isEmpty() || (!direct && (hik == null || hik.isEmpty()))) throw new ServiceException("授权凭据不完整");
                synchronized (job) { target.state = "VERIFYING"; target.attempts = index + 1; target.candidate = String.valueOf(candidate.get("name")); }
                var login = verifier.verify(target.address, target.chosen.port(), direct ? "root" : "hik", direct ? root : hik,
                    root, direct, target.chosen.fingerprint());
                if (!Set.of("HOST_KEY_REQUIRED", "HOST_KEY_CHANGED").contains(login.status())) lease.attempted();
                synchronized (job) { target.message = login.message(); target.mode = login.mode(); }
                if ("SUCCESS".equals(login.status()) && login.rootPasswordVerified())
                {
                    if (job.cancelled) return;
                    var facts = probe.collect(target.address, target.chosen.port(), direct ? "root" : "hik", direct ? root : hik, target.chosen.fingerprint());
                    if (job.cancelled) return;
                    Long previousId = target.original == null ? null : ((Number) target.original.get("sourceId")).longValue();
                    Long serverId = sync.sync(job.siteId, job.platformId, target.address, previousId,
                        target.original == null ? null : String.valueOf(target.original.get("revision")), target.chosen.port(), direct,
                        hik, root, facts, target.ports.stream().filter(SupportServerScanProbe.Port::open).map(port -> String.valueOf(port.port())).reduce((a, b) -> a + "," + b).orElse(""), job.actor);
                    synchronized (job)
                    {
                        target.serverId = serverId; target.facts = facts.values(); target.state = "SYNCED";
                        target.message = (previousId == null ? "已新建服务器并归属选定子平台" : "已同步服务器，原有平台归属保持不变")
                            + (facts.warning().isEmpty() ? "" : "；" + facts.warning());
                    }
                    return;
                }
                if (!Set.of("AUTH_REJECTED", "SU_REJECTED").contains(login.status())) break;
                if (index + 1 < Math.min(job.attemptLimit, job.candidates.size())) Thread.sleep(2000);
            }
            synchronized (job) { target.state = "FAILED"; }
        }
        catch (Exception error)
        {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            synchronized (job)
            {
                target.state = "FAILED";
                target.message = error instanceof ServiceException ? error.getMessage() : "验证或同步未完成，未写入本次结果，请检查服务配置后重试";
            }
        }
    }

    private void execute(Job job, List<Target> targets, Consumer<Target> action, String completed, SecurityContext security)
    {
        coordinators.submit(() -> {
            try
            {
                List<Future<?>> futures = new ArrayList<>();
                for (Target target : targets)
                {
                    Runnable work = () -> {
                        try { if (!job.cancelled) action.accept(target); }
                        finally { synchronized (job) { job.done++; } }
                    };
                    futures.add(workers.submit(security == null ? work : new DelegatingSecurityContextRunnable(work, security)));
                }
                for (Future<?> future : futures) future.get();
                synchronized (job)
                {
                    if (job.cancelled) for (Target target : targets)
                    {
                        if (Set.of("PENDING", "VERIFYING").contains(target.state)) { target.state = "SKIPPED"; target.message = "任务已停止，本次未同步"; }
                    }
                    job.state = job.cancelled ? "CANCELLED" : completed; job.finished = System.currentTimeMillis();
                }
            }
            catch (Exception error)
            {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                synchronized (job) { job.cancelled = true; job.state = "FAILED"; job.finished = System.currentTimeMillis(); job.error = "扫描任务未完成，请重新核对目标后重试"; }
            }
        });
    }
    private Job owned(String id, Long userId)
    {
        prune();
        Job job = jobs.get(id);
        if (job == null || !job.userId.equals(userId)) throw new ServiceException("任务不存在、已过期或不属于当前用户");
        return job;
    }
    private void prune()
    {
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(entry -> !RUNNING.contains(entry.getValue().state)
            && now - Math.max(entry.getValue().created, entry.getValue().finished) > 30 * 60000L);
    }
    static boolean sameCredentials(List<Map<String, Object>> first, List<Map<String, Object>> second)
    {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) for (String key : List.of("credentialId", "mode", "hikCipher", "rootCipher"))
            if (!Objects.equals(first.get(i).get(key), second.get(i).get(key))) return false;
        return true;
    }
    private static ThreadFactory threads(String prefix)
    {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> { Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet()); thread.setDaemon(true); return thread; };
    }
    @PreDestroy public void close() { jobs.values().forEach(job -> job.cancelled = true); coordinators.shutdownNow(); workers.shutdownNow(); }

    private static final class Target
    {
        final String address;
        final Map<String, Object> original;
        List<SupportServerScanProbe.Port> ports = List.of();
        SupportServerScanProbe.Port chosen;
        String state = "PENDING", message = "等待探测", candidate = "", mode = "";
        int attempts;
        Long serverId;
        Map<String, String> facts = Map.of();
        Target(String address, Map<String, Object> original) { this.address = address; this.original = original; }
        Map<String, Object> view()
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("address", address); result.put("existing", original != null); result.put("ports", ports);
            result.put("serverId", serverId == null && original != null && "SERVER".equals(original.get("sourceType")) ? original.get("sourceId") : serverId);
            result.put("serverName", original == null ? "" : original.get("assetName")); result.put("registeredPort", original == null ? null : original.get("sshPort"));
            result.put("state", state); result.put("message", message); result.put("attempts", attempts); result.put("candidate", candidate);
            result.put("mode", mode); result.put("facts", facts); result.put("port", chosen == null ? null : chosen.port());
            return result;
        }
    }
    private static final class Job
    {
        final String id = UUID.randomUUID().toString(), actor;
        final Long siteId, platformId, userId;
        final List<Integer> ports;
        final List<Map<String, Object>> candidates;
        final int attemptLimit;
        final long created = System.currentTimeMillis();
        final Map<String, Target> rows = new LinkedHashMap<>();
        volatile String state = "DISCOVERING";
        volatile boolean cancelled;
        volatile long finished;
        String error = "";
        int done, total;
        Job(Long siteId, Long platformId, Long userId, String actor, List<Integer> ports, List<Map<String, Object>> candidates, int limit)
        { this.siteId = siteId; this.platformId = platformId; this.userId = userId; this.actor = actor; this.ports = ports; this.candidates = candidates; this.attemptLimit = limit; }
        synchronized Map<String, Object> view()
        {
            return Map.of("taskId", id, "siteId", siteId, "platformId", platformId, "state", state, "done", done,
                "total", total == 0 ? rows.size() : total, "error", error, "rows", rows.values().stream().map(Target::view).toList(),
                "createdAt", java.time.Instant.ofEpochMilli(created).toString());
        }
    }
}
