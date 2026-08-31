package com.aii.domain;

/**
 * Incident lifecycle, per the design doc's state diagram:
 * DETECTED -> INVESTIGATING -> IDENTIFIED -> MITIGATING -> RESOLVED -> POSTMORTEM
 */
public enum IncidentStatus {
    DETECTED,
    INVESTIGATING,
    IDENTIFIED,
    MITIGATING,
    RESOLVED,
    POSTMORTEM;

    /** Enforces valid forward transitions only - no skipping steps, no going backward. */
    public boolean canTransitionTo(IncidentStatus next) {
        return this.ordinal() + 1 == next.ordinal();
    }
}
