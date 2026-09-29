package com.hm.manage.service.support;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import com.hm.common.exception.ServiceException;

/** Shared by one-device checks and discovery: do not multiply attempts through different entry points. */
@Component
public class SupportServerLoginGuard
{
    private final Semaphore capacity = new Semaphore(4);
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> nextAttempt = new ConcurrentHashMap<>();

    public Lease acquire(String host)
    {
        String key = SupportServerScanTargets.canonical(host).trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        nextAttempt.entrySet().removeIf(entry -> entry.getValue() < now);
        if (!running.add(key)) throw new ServiceException("该地址正在校验，请勿重复提交");
        if (nextAttempt.getOrDefault(key, 0L) > now)
        {
            running.remove(key);
            throw new ServiceException("该地址刚执行过登录校验，请等待60秒后再试，避免账号锁定");
        }
        if (!capacity.tryAcquire())
        {
            running.remove(key);
            throw new ServiceException("登录校验任务繁忙，请稍后重试");
        }
        return new Lease(key);
    }

    public final class Lease implements AutoCloseable
    {
        private final String key;
        private boolean attempted, closed;
        private Lease(String key) { this.key = key; }
        public void attempted() { attempted = true; }
        public void close()
        {
            if (closed) return;
            closed = true;
            if (attempted) nextAttempt.put(key, System.currentTimeMillis() + 60000);
            running.remove(key);
            capacity.release();
        }
    }
}
