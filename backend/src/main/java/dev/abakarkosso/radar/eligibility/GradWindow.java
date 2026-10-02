package dev.abakarkosso.radar.eligibility;

import java.time.YearMonth;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The graduation dates a posting accepts. Either end may be open (null).
 *
 * Seasons map to the month a term of that name ends: Spring is May, Summer is August, Fall is December.
 * Winter is ambiguous: the Canadian Winter term ends in April, but US postings ("Winter 2027 or Spring
 * 2028") mean December. The window is used to warn, so it takes the wider reading: April as a lower
 * bound, December as an upper bound.
 */
public record GradWindow(YearMonth earliest, YearMonth latest) {

    private static final int I = Pattern.CASE_INSENSITIVE;

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12),
            Map.entry("spring", 5), Map.entry("summer", 8), Map.entry("fall", 12), Map.entry("autumn", 12));

    // "August 2027", "Aug. 2027", "Fall 2027", "spring/summer 2028" (the later season counts)
    private static final String DATE = "(?:(?:spring|summer|fall|autumn|winter)/)?"
            + "((?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?|spring|summer|fall|autumn|winter)\\s+(20\\d\\d)";

    private static final Pattern CLAUSE_START = Pattern.compile("graduat[a-z]*|conferral", I);
    // A sentence ends at a full stop followed by space, except after a month or degree abbreviation.
    private static final Pattern SENTENCE_END = Pattern.compile(
            "(?<!\\b(?:jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec|b\\.sc|m\\.sc|ph\\.d|e\\.g|i\\.e))\\.(?:\\s|$)|\\n", I);
    private static final Pattern CLASS_OF = Pattern.compile("\\bclass of (20\\d\\d)\\b", I);

    private static final Pattern BETWEEN = Pattern.compile(
            "between\\s+" + DATE + "\\s+(?:and|to|-|\\u2013|\\u2014)\\s+" + DATE, I);
    private static final Pattern EITHER = Pattern.compile(DATE + "\\s+or\\s+" + DATE, I);
    private static final Pattern AFTER = Pattern.compile("(?<!on or )\\bafter\\s+" + DATE, I);
    private static final Pattern FROM = Pattern.compile(
            "(?:no earlier than|on or after)\\s+" + DATE + "|" + DATE + "\\s+or\\s+(?:beyond|later|after)", I);
    private static final Pattern BEFORE = Pattern.compile("\\bbefore\\s+" + DATE, I);
    private static final Pattern UNTIL = Pattern.compile("(?:\\bby|no later than)\\s+" + DATE, I);
    private static final Pattern MID_LATE = Pattern.compile("\\bmid-?\\s*(?:to-?\\s*)?late\\s+(20\\d\\d)", I);

    /** Keeps the window ordered even when a posting lists the later date first. */
    public GradWindow {
        if (earliest != null && latest != null && earliest.isAfter(latest)) {
            YearMonth swap = earliest;
            earliest = latest;
            latest = swap;
        }
    }

    /** The window stated in the text, or null when the text bounds graduation by no date. */
    public static GradWindow parse(String text) {
        if (text == null) {
            return null;
        }
        Matcher classOf = CLASS_OF.matcher(text);
        if (classOf.find()) {
            int year = Integer.parseInt(classOf.group(1));
            return new GradWindow(YearMonth.of(year, 1), YearMonth.of(year, 12));
        }
        Matcher start = CLAUSE_START.matcher(text);
        while (start.find()) {
            String rest = text.substring(start.end(), Math.min(text.length(), start.end() + 160));
            Matcher end = SENTENCE_END.matcher(rest);
            GradWindow w = parseClause(end.find() ? rest.substring(0, end.start()) : rest);
            if (w != null) {
                return w;
            }
        }
        return null;
    }

    private static GradWindow parseClause(String c) {
        Matcher m;
        if ((m = BETWEEN.matcher(c)).find()) {
            return new GradWindow(date(m, 1, true), date(m, 3, false));
        }
        if ((m = FROM.matcher(c)).find()) {
            return new GradWindow(m.group(1) != null ? date(m, 1, true) : date(m, 3, true), null);
        }
        if ((m = AFTER.matcher(c)).find()) {
            return new GradWindow(date(m, 1, false).plusMonths(1), null);
        }
        if ((m = EITHER.matcher(c)).find()) {
            // Whichever date is earlier is the lower bound; the other is read as the upper bound.
            YearMonth a = date(m, 1, true);
            YearMonth b = date(m, 3, true);
            return a.isAfter(b) ? new GradWindow(b, date(m, 1, false)) : new GradWindow(a, date(m, 3, false));
        }
        if ((m = BEFORE.matcher(c)).find()) {
            return new GradWindow(null, date(m, 1, true).minusMonths(1));
        }
        if ((m = UNTIL.matcher(c)).find()) {
            return new GradWindow(null, date(m, 1, false));
        }
        if ((m = MID_LATE.matcher(c)).find()) {
            int year = Integer.parseInt(m.group(1));
            return new GradWindow(YearMonth.of(year, 6), YearMonth.of(year, 12));
        }
        return null;
    }

    /** Reads the month or season at {@code index} and the year after it. */
    private static YearMonth date(Matcher m, int index, boolean lowerBound) {
        String word = m.group(index).toLowerCase();
        int month = word.equals("winter") ? (lowerBound ? 4 : 12)
                : MONTHS.containsKey(word) ? MONTHS.get(word) : MONTHS.get(word.substring(0, 3));
        return YearMonth.of(Integer.parseInt(m.group(index + 1)), month);
    }
}
