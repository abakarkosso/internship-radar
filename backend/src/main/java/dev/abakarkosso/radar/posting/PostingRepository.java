package dev.abakarkosso.radar.posting;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<Posting, Long>, JpaSpecificationExecutor<Posting> {

    Optional<Posting> findBySourceAndExternalId(String source, String externalId);

    List<Posting> findBySource(String source);

    @Modifying
    @Query("delete from Posting p where p.open = false and p.lastSeenAt < :cutoff")
    int deleteClosedBefore(@Param("cutoff") Instant cutoff);

    long countByOpenTrue();

    @Query("select count(distinct p.company) from Posting p where p.open = true")
    long countOpenCompanies();

    @Query("select max(p.lastSeenAt) from Posting p")
    Optional<Instant> lastSeen();
}
