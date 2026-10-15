package com.clinic.appointment.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    /** Permanent failure (e.g. record too large). Parked so it cannot block later events; needs manual attention. */
    FAILED
}
