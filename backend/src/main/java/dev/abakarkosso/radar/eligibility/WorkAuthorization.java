package dev.abakarkosso.radar.eligibility;

/** Who a posting can hire, strictest first. */
public enum WorkAuthorization {
    /** Canadian citizens and permanent residents only (often government-funded placements). */
    CITIZEN_OR_PR,
    /** Anyone already authorized to work in Canada; the employer will not sponsor a permit. */
    NO_SPONSORSHIP,
    UNSPECIFIED
}
