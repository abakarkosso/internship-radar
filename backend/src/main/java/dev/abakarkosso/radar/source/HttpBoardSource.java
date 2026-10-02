package dev.abakarkosso.radar.source;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/** A board behind one public JSON endpoint. The parser turns the response body into postings. */
public class HttpBoardSource implements PostingSource {

    private final String key;
    private final URI uri;
    private final HttpClient http;
    private final Function<String, List<RawPosting>> parser;

    public HttpBoardSource(String key, URI uri, HttpClient http, Function<String, List<RawPosting>> parser) {
        this.key = key;
        this.uri = uri;
        this.http = http;
        this.parser = parser;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public List<RawPosting> fetch() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "internship-radar")
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(key + " returned HTTP " + response.statusCode());
        }
        return parser.apply(response.body());
    }
}
