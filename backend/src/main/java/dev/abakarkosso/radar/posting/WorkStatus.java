package dev.abakarkosso.radar.posting;

/** A student's right to work in Canada, as they report it in their profile. */
public enum WorkStatus {
    CITIZEN_OR_PR,
    /** Holds a permit (e.g. a study permit with work rights) and needs no employer sponsorship. */
    WORK_PERMIT,
    NEEDS_SPONSORSHIP
}
