package dev.abakarkosso.radar.posting;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/postings")
class PostingController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PostingRepository repository;

    PostingController(PostingRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    FeedPage list(@RequestParam(required = false) @Size(max = 100) String q,
                              @RequestParam(required = false) RoleCategory category,
                              @RequestParam(defaultValue = "false") boolean excludeCoopRequired,
                              @RequestParam(defaultValue = "false") boolean excludeGraduateOnly,
                              @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
                              @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(page, Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "firstSeenAt"));
        return FeedPage.of(repository.findAll(PostingFilters.build(q, category, excludeCoopRequired, excludeGraduateOnly), pageable)
                .map(PostingSummary::of));
    }

    /** Header numbers for the UI, including when the radar last heard from a board. */
    @GetMapping("/stats")
    Stats stats() {
        return new Stats(repository.countByOpenTrue(), repository.countOpenCompanies(),
                repository.lastSeen().orElse(null));
    }

    record Stats(long openPostings, long companies, java.time.Instant lastCheckedAt) {
    }
}
