package org.dacrewj.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Minimal Atlassian Document Format (ADF) model suitable for Jira Cloud comment bodies.
 * See: <a href="https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/">...</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdfDocument(
        String type,
        int version,
        List<Node> content
) {
    public AdfDocument {
        // Default type/version if not provided
        if (type == null || type.isBlank()) type = "doc";
        if (version == 0) version = 1;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Node(
            String type,
            Map<String, Object> attrs,
            List<Node> content,
            String text,
            List<Mark> marks
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Mark(
            String type,
            Map<String, Object> attrs
    ) {}

    /**
     * Compute a simple plain-text preview by concatenating text nodes depth-first up to maxLen.
     */
    public String previewText(int maxLen) {
        StringBuilder sb = new StringBuilder();
        appendText(content, sb, maxLen);
        if (sb.length() > maxLen) {
            return sb.substring(0, maxLen);
        }
        return sb.toString();
    }

    private static void appendText(List<Node> nodes, StringBuilder sb, int maxLen) {
        if (nodes == null || sb.length() >= maxLen) return;
        for (Node n : nodes) {
            if (sb.length() >= maxLen) break;
            if (n == null) continue;
            if (n.text() != null) {
                int remaining = maxLen - sb.length();
                String t = n.text();
                sb.append(t, 0, Math.min(remaining, t.length()));
            }
            if (n.content() != null && !n.content().isEmpty()) {
                appendText(n.content(), sb, maxLen);
            }
        }
    }
}
