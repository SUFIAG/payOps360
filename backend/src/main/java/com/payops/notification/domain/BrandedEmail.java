package com.payops.notification.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders PayOps360-branded transactional e-mail as plain text and HTML.
 * Deliberately simple: inline styles and a single-column table layout so the
 * message renders consistently across mail clients. Every dynamic value is
 * HTML-escaped.
 */
public final class BrandedEmail {

    private static final String BRAND = "PayOps360";
    private static final String INK = "#12161c";
    private static final String JADE = "#0e7c5b";
    private static final String MUTED = "#5f6670";

    private final String heading;
    private final List<String> paragraphs = new ArrayList<>();
    private String actionLabel;
    private String actionUrl;
    private String footnote;

    private BrandedEmail(String heading) {
        this.heading = heading;
    }

    public static BrandedEmail titled(String heading) {
        return new BrandedEmail(heading);
    }

    public BrandedEmail paragraph(String text) {
        paragraphs.add(text);
        return this;
    }

    public BrandedEmail action(String label, String url) {
        this.actionLabel = label;
        this.actionUrl = url;
        return this;
    }

    /** Small print under the main content, e.g. "If you did not request this ...". */
    public BrandedEmail footnote(String text) {
        this.footnote = text;
        return this;
    }

    public EmailContent render() {
        return new EmailContent(text(), html());
    }

    private String text() {
        StringBuilder sb = new StringBuilder();
        sb.append(heading).append("\n\n");
        paragraphs.forEach(p -> sb.append(p).append("\n\n"));
        if (actionUrl != null) {
            sb.append(actionLabel).append(":\n").append(actionUrl).append("\n\n");
        }
        if (footnote != null) {
            sb.append(footnote).append("\n\n");
        }
        sb.append("-- \n").append(BRAND).append(" - payment operations command center\n")
                .append("This is an automated security message; please do not reply.\n");
        return sb.toString();
    }

    private String html() {
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            body.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:").append(INK).append("\">")
                    .append(escape(p)).append("</p>");
        }
        if (actionUrl != null) {
            String url = escape(actionUrl);
            body.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 24px\"><tr>")
                    .append("<td style=\"border-radius:8px;background:").append(JADE).append("\">")
                    .append("<a href=\"").append(url).append("\" style=\"display:inline-block;padding:12px 22px;")
                    .append("font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:8px\">")
                    .append(escape(actionLabel)).append("</a></td></tr></table>")
                    .append("<p style=\"margin:0 0 16px;font-size:13px;line-height:1.6;color:").append(MUTED)
                    .append("\">If the button does not work, copy this link into your browser:<br>")
                    .append("<a href=\"").append(url).append("\" style=\"color:").append(JADE)
                    .append(";word-break:break-all\">").append(url).append("</a></p>");
        }
        if (footnote != null) {
            body.append("<p style=\"margin:24px 0 0;padding-top:16px;border-top:1px solid #e6e3dc;font-size:13px;")
                    .append("line-height:1.6;color:").append(MUTED).append("\">").append(escape(footnote)).append("</p>");
        }
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + escape(heading)
                + "</title></head><body style=\"margin:0;padding:0;background:#f4f2ed;"
                + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f2ed\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px\">"
                + "<tr><td style=\"padding:0 4px 16px;font-size:18px;font-weight:700;letter-spacing:-0.01em;color:" + INK + "\">"
                + "PayOps<span style=\"color:" + JADE + "\">360</span></td></tr>"
                + "<tr><td style=\"background:#ffffff;border:1px solid #e6e3dc;border-radius:14px;padding:32px\">"
                + "<h1 style=\"margin:0 0 20px;font-size:22px;line-height:1.3;font-weight:600;color:" + INK + "\">"
                + escape(heading) + "</h1>" + body
                + "</td></tr>"
                + "<tr><td style=\"padding:16px 4px;font-size:12px;line-height:1.6;color:" + MUTED + "\">"
                + BRAND + " &middot; payment operations command center<br>"
                + "This is an automated security message; please do not reply.</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
