package dev.abakarkosso.radar.ingest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pipeline health for the UI: how many boards are configured and what each one's last run saw. */
@RestController
class PipelineController {

    private final IngestionService ingestion;

    PipelineController(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @GetMapping("/api/pipeline")
    Pipeline pipeline() {
        return new Pipeline(ingestion.boardCount(), ingestion.boardStatuses());
    }

    record Pipeline(int boards, List<IngestionService.BoardStatus> statuses) {
    }
}
