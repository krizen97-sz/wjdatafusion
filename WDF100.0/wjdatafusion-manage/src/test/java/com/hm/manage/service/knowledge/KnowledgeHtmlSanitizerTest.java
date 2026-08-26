package com.hm.manage.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class KnowledgeHtmlSanitizerTest
{
    private final KnowledgeHtmlSanitizer sanitizer = new KnowledgeHtmlSanitizer();

    @Test
    void shouldPreserveKnowledgeMarkupAndRemoveExecutableContent()
    {
        String cleaned = sanitizer.sanitize("<h2>检查</h2><p class=\"ql-align-center\">正文</p>"
            + "<script>alert(1)</script><a href=\"javascript:alert(2)\">链接</a>");

        assertTrue(cleaned.contains("<h2>检查</h2>"));
        assertTrue(cleaned.contains("ql-align-center"));
        assertFalse(cleaned.contains("<script"));
        assertFalse(cleaned.contains("javascript:"));
    }

    @Test
    void shouldKeepRelativeImagesAndRemoveRemoteTrackingImages()
    {
        String cleaned = sanitizer.sanitize("<p><img src=\"/profile/upload/local.png\" alt=\"本地图\">"
            + "<img src=\"https://tracker.example/pixel.png\" onerror=\"alert(1)\">"
            + "<img src=\"//tracker.example/protocol-relative.png\"></p>");

        assertTrue(cleaned.contains("/profile/upload/local.png"));
        assertFalse(cleaned.contains("tracker.example"));
        assertFalse(cleaned.contains("onerror"));
    }

    @Test
    void shouldRemoveQuillEmptyParagraphNoiseWithoutRemovingLocalImages()
    {
        String cleaned = sanitizer.sanitize("<h2>排查步骤</h2>\n<p><br></p>\n<p>&nbsp;</p>\n"
            + "<p>确认告警</p><p><img src=\"/profile/upload/evidence.png\"></p>");

        assertFalse(cleaned.contains("<p><br></p>"));
        assertFalse(cleaned.contains("&nbsp;"));
        assertTrue(cleaned.contains("<p>确认告警</p>"));
        assertTrue(cleaned.contains("/profile/upload/evidence.png"));
        assertTrue(cleaned.startsWith("<h2>排查步骤</h2><p>确认告警</p>"));
    }
}
