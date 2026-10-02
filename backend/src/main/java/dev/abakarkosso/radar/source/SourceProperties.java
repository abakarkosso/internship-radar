package dev.abakarkosso.radar.source;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "radar.sources")
public record SourceProperties(List<Board> greenhouse, List<Board> ashby, List<Board> lever) {

    public record Board(String board, String company) {
    }

    public SourceProperties {
        greenhouse = greenhouse == null ? List.of() : greenhouse;
        ashby = ashby == null ? List.of() : ashby;
        lever = lever == null ? List.of() : lever;
    }
}
