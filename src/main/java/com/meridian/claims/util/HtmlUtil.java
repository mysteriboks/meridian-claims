package com.meridian.claims.util;

/**
 * Minimal HTML escaping for server-generated document content (EOBs, remittance
 * advice) where dynamic values are concatenated into an HTML string outside the
 * JSP/JSTL layer. JSP views use {@code <c:out>}; this is the equivalent for the
 * hand-built HTML produced in the service layer.
 */
public final class HtmlUtil {

    private HtmlUtil() {}

    /** Escapes &amp;, &lt;, and &gt; so dynamic data cannot inject markup. Null becomes "". */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
