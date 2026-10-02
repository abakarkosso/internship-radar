package dev.abakarkosso.radar.source;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

final class Html {

    private Html() {
    }

    /** Job boards often send entity-escaped HTML ("&lt;p&gt;"), so unescape before parsing. */
    static String toText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(Parser.unescapeEntities(html, false)).text();
    }
}
