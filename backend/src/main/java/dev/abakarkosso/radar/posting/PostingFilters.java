package dev.abakarkosso.radar.posting;

import java.time.Instant;

import dev.abakarkosso.radar.eligibility.CoopRequirement;
import dev.abakarkosso.radar.eligibility.WorkAuthorization;
import org.springframework.data.jpa.domain.Specification;

/** Query building blocks for the feed. Each filter is optional and they combine with AND. */
final class PostingFilters {

    private PostingFilters() {
    }

    static Specification<Posting> build(String q, RoleCategory category, boolean excludeCoopRequired,
                                        boolean excludeGraduateOnly, WorkStatus workStatus, Instant postedAfter) {
        Specification<Posting> spec = (root, query, cb) -> cb.isTrue(root.get("open"));
        if (q != null && !q.isBlank()) {
            String like = "%" + q.toLowerCase().strip() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("company")), like)));
        }
        if (category != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }
        if (excludeCoopRequired) {
            spec = spec.and((root, query, cb) ->
                    cb.notEqual(root.get("coopRequirement"), CoopRequirement.REQUIRED));
        }
        if (excludeGraduateOnly) {
            spec = spec.and((root, query, cb) -> cb.isFalse(root.get("graduateDegreeRequired")));
        }
        if (workStatus == WorkStatus.WORK_PERMIT) {
            spec = spec.and((root, query, cb) ->
                    cb.notEqual(root.get("workAuthorization"), WorkAuthorization.CITIZEN_OR_PR));
        } else if (workStatus == WorkStatus.NEEDS_SPONSORSHIP) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("workAuthorization"), WorkAuthorization.UNSPECIFIED));
        }
        if (postedAfter != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("firstSeenAt"), postedAfter));
        }
        return spec;
    }
}
