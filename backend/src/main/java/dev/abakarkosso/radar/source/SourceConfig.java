package dev.abakarkosso.radar.source;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(SourceProperties.class)
class SourceConfig {

    @Bean
    SourceRegistry sourceRegistry(SourceProperties props, ObjectMapper mapper) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Board APIs answer directly; refusing redirects keeps fetches on the hosts named in config.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        GreenhouseParser greenhouse = new GreenhouseParser(mapper);
        AshbyParser ashby = new AshbyParser(mapper);
        LeverParser lever = new LeverParser(mapper);

        List<PostingSource> sources = new ArrayList<>();
        for (var b : props.greenhouse()) {
            String key = "greenhouse:" + b.board();
            sources.add(new HttpBoardSource(key,
                    uri("https://boards-api.greenhouse.io/v1/boards/%s/jobs?content=true", b.board()), http,
                    body -> greenhouse.parse(body, key, b.company())));
        }
        for (var b : props.ashby()) {
            String key = "ashby:" + b.board();
            sources.add(new HttpBoardSource(key,
                    uri("https://api.ashbyhq.com/posting-api/job-board/%s", b.board()), http,
                    body -> ashby.parse(body, key, b.company())));
        }
        for (var b : props.lever()) {
            String key = "lever:" + b.board();
            sources.add(new HttpBoardSource(key,
                    uri("https://api.lever.co/v0/postings/%s?mode=json", b.board()), http,
                    body -> lever.parse(body, key, b.company())));
        }
        return new SourceRegistry(List.copyOf(sources));
    }

    /** Board names come from config, but encode them anyway so a typo can't change the path. */
    private static URI uri(String template, String board) {
        return URI.create(template.formatted(URLEncoder.encode(board, StandardCharsets.UTF_8)));
    }
}
