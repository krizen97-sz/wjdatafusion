package com.hm.manage.service.governance.hdi;

/** Safe, user-facing management error; vendor exceptions stay in private server diagnostics. */
public final class HdiTaskFault extends RuntimeException {
    private final int status;
    public HdiTaskFault(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
