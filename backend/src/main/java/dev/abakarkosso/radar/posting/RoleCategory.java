package dev.abakarkosso.radar.posting;

import java.util.regex.Pattern;

public enum RoleCategory {
    HARDWARE("hardware|firmware|embedded|asic|fpga|analog|silicon|pcb|electrical"),
    // "Research" alone means AI research here, but not UX or market research.
    AI_ML("\\bai\\b|machine learning|\\bml\\b|deep learning|computer vision|nlp|llm"
            + "|(?<!ux )(?<!user )(?<!market )(?<!marketing )\\bresearch\\b"),
    DATA("data|analyt|business intelligence|\\bbi\\b"),
    QUANT("quant|trading|actuar"),
    SOFTWARE("software|developer|full.?stack|back.?end|front.?end|web|mobile|devops|platform|cloud|engineer"),
    PRODUCT("product|business|program manag|project manag"),
    OTHER("$^");

    private final Pattern pattern;

    RoleCategory(String regex) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /** First match wins, so the order above runs from most to least specific. */
    public static RoleCategory fromTitle(String title) {
        for (RoleCategory c : values()) {
            if (c.pattern.matcher(title).find()) {
                return c;
            }
        }
        return OTHER;
    }
}
