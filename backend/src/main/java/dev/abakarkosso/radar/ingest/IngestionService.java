package dev.abakarkosso.radar.ingest;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.abakarkosso.radar.eligibility.EligibilityExtractor;
import dev.abakarkosso.radar.posting.Posting;
import dev.abakarkosso.radar.posting.PostingRepository;
import dev.abakarkosso.radar.source.PostingSource;
import dev.abakarkosso.radar.source.RawPosting;
import dev.abakarkosso.radar.source.SourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IngestionService {

    private static final java.time.Duration RETENTION = java.time.Duration.ofDays(90);

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    public record RunResult(String source, int fetched, int kept, int created, int closed) {
    }

    private final SourceRegistry registry;
    private final PostingRepository repository;
    private final EligibilityExtractor extractor;
    private final TransactionTemplate tx;
    private final Clock clock;

    public IngestionService(SourceRegistry registry, PostingRepository repository, EligibilityExtractor extractor,
                            TransactionTemplate tx, Clock clock) {
        this.registry = registry;
        this.repository = repository;
        this.extractor = extractor;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${radar.ingest.interval:PT15M}", initialDelayString = "${radar.ingest.initial-delay:PT10S}")
    public void runAll() {
        runAllFor(registry.sources());
    }

    void runAllFor(List<PostingSource> sources) {
        for (PostingSource source : sources) {
            try {
                RunResult r = run(source);
                log.info("Ingested {}: fetched={} kept={} new={} closed={}",
                        r.source(), r.fetched(), r.kept(), r.created(), r.closed());
            } catch (Exception e) {
                // One broken board must not stop the others, and must not close its postings.
                log.warn("Skipping {}: {}", source.key(), e.toString());
            }
        }
    }

    /** Closed postings are kept a while so a posting that reappears keeps its first-seen time. */
    @Scheduled(cron = "${radar.ingest.purge-cron:0 30 4 * * *}")
    public void purgeOldClosed() {
        int removed = tx.execute(status -> repository.deleteClosedBefore(clock.instant().minus(RETENTION)));
        if (removed > 0) {
            log.info("Purged {} postings closed for more than {} days", removed, RETENTION.toDays());
        }
    }

    /** Fetch one board and reconcile it with the database in a single transaction. */
    public RunResult run(PostingSource source) throws Exception {
        List<RawPosting> fetched = source.fetch();
        List<RawPosting> kept = fetched.stream().filter(InternshipFilter::accepts).toList();
        Instant now = clock.instant();

        return tx.execute(status -> {
            int created = 0;
            Set<String> seen = new HashSet<>();
            Map<String, Posting> existing = new HashMap<>();
            for (Posting p : repository.findBySource(source.key())) {
                existing.put(p.getExternalId(), p);
            }
            for (RawPosting raw : kept) {
                if (!seen.add(raw.externalId())) {
                    continue; // boards occasionally list the same job twice
                }
                Posting posting = existing.get(raw.externalId());
                if (posting == null) {
                    posting = new Posting(source.key(), raw.externalId(), now);
                    created++;
                }
                posting.refresh(raw.company(), raw.title(), InternshipFilter.displayLocation(raw.location()), raw.url(), raw.description(),
                        extractor.extract(raw.title() + "\n" + raw.description()), now);
                repository.save(posting);
            }
            int closed = 0;
            for (Posting p : existing.values()) {
                if (p.isOpen() && !seen.contains(p.getExternalId())) {
                    p.close();
                    closed++;
                }
            }
            return new RunResult(source.key(), fetched.size(), kept.size(), created, closed);
        });
    }
}
