package com.hm.manage.service.governance;

import org.springframework.stereotype.Component;

/** Scheduling delegates delivery to the same durable, idempotent path as manual delivery. */
@Component
public class DataGovernanceScheduleDeliveryAdapter implements DataGovernanceScheduleDelivery
{
    private final DataGovernanceFtpDelivery ftp;
    public DataGovernanceScheduleDeliveryAdapter(DataGovernanceFtpDelivery ftp) { this.ftp = ftp; }
    @Override public Binding target(String connectionId, long owner)
    {
        var target = ftp.destination(connectionId, owner);
        return new Binding(target.id(), target.name(), DataGovernanceFtpDelivery.targetFingerprint(target));
    }
    @Override public Delivery submit(String runId, Binding binding, long owner)
    { return value(ftp.submit(new DataGovernanceFtpDelivery.Submit(runId, binding.id()), owner, binding.fingerprint()), owner); }
    @Override public Delivery find(String runId, String connectionId, long owner)
    {
        return ftp.jobs(runId, owner).stream().filter(job -> job.connectionId().equals(connectionId))
            .findFirst().map(job -> value(job, owner)).orElse(null);
    }
    @Override public Delivery status(String deliveryId, long owner) { return value(ftp.job(deliveryId, owner), owner); }
    private Delivery value(DataGovernanceFtpDelivery.Job job, long owner) { return new Delivery(job.id(), job.status(), job.error(), ftp.deliveryTargetFingerprint(job.id(), owner)); }
}
