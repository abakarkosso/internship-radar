package dev.abakarkosso.radar.ingest;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import dev.abakarkosso.radar.source.RawPosting;

/** Keeps only student roles located in Canada (including remote-in-Canada). */
final class InternshipFilter {

    // "student" and French "stage" only in their internship senses: "IT Student", "Stage - Développement",
    // not "Student Success Manager" or "Early-Stage Products".
    private static final Pattern STUDENT_ROLE = Pattern.compile(
            "\\bintern(ship)?s?\\b|co-?op|\\bstagiaire\\b|^stage\\b"
            + "|\\bstudent\\s*(\\(|-|intern|co-?op|developer|engineer|analyst|program|position|opportunit)"
            + "|(\\bit|summer|winter|fall|engineering|technology|software|data)\\s+student\\b",
            Pattern.CASE_INSENSITIVE);

    // Province codes stay case-sensitive so "on" in free text doesn't count; place names don't.
    private static final Pattern CANADA = Pattern.compile(
            "\\b(ON|BC|QC|AB|MB|SK|NS|NB|NL|PE)\\b|(?i:canada|toronto|vancouver|montr[eé]al|ottawa|waterloo"
            + "|calgary|edmonton|kitchener|mississauga|markham|halifax|winnipeg|qu[eé]bec|ontario"
            + "|british columbia|alberta|manitoba|saskatchewan|nova scotia|new brunswick|newfoundland"
            + "|prince edward island)");

    private InternshipFilter() {
    }

    static boolean accepts(RawPosting p) {
        return p.title() != null
                && p.externalId() != null && p.externalId().length() <= 200
                && STUDENT_ROLE.matcher(p.title()).find()
                && CANADA.matcher(p.location() == null ? "" : p.location()).find()
                && isWebLink(p.url());
    }

    /** Canadian locations first, the rest folded: "Canada, Toronto + 3 more". */
    static String displayLocation(String location) {
        if (location == null) {
            return "";
        }
        List<String> parts = Arrays.stream(location.split(" / ")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        List<String> canadian = parts.stream().filter(s -> CANADA.matcher(s).find()).toList();
        int others = parts.size() - canadian.size();
        return canadian.isEmpty() || others == 0 ? String.join(", ", parts)
                : String.join(", ", canadian) + " + " + others + " more";
    }

    /** The UI renders the link as an href, so anything but http(s) (e.g. "javascript:") is dropped. */
    static boolean isWebLink(String url) {
        if (url == null || url.length() > 2000) {
            return false;
        }
        try {
            String scheme = java.net.URI.create(url.strip()).getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
