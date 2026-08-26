package com.hm.manage.service.knowledge;

import java.util.ArrayList;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import com.hm.common.utils.StringUtils;

@Component
public class KnowledgeHtmlSanitizer
{
    private static final Set<String> ROOT_BLOCK_TAGS = Set.of("address", "article", "aside", "blockquote",
        "div", "dl", "fieldset", "figure", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
        "header", "hr", "main", "nav", "ol", "p", "pre", "section", "table", "ul");

    private static final Safelist SAFELIST = Safelist.relaxed()
        .addTags("h1", "h2", "h3", "h4", "h5", "h6", "pre", "code", "blockquote",
            "span", "u", "s", "sub", "sup")
        .addAttributes("p", "class")
        .addAttributes("span", "class")
        .addAttributes("li", "class", "data-list")
        .addAttributes("a", "target", "rel")
        .addAttributes("img", "data-kb-src")
        .preserveRelativeLinks(true);

    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings()
        .prettyPrint(false);

    public String sanitize(String html)
    {
        if (StringUtils.isBlank(html))
        {
            return "";
        }
        Document sourceDocument = Jsoup.parseBodyFragment(html);
        for (Element image : sourceDocument.select("img[src]"))
        {
            String source = image.attr("src").trim();
            if ((source.startsWith("/") && !source.startsWith("//")) || source.startsWith("./"))
            {
                image.attr("data-kb-src", source);
                image.removeAttr("src");
            }
            else
            {
                image.remove();
            }
        }
        String cleaned = Jsoup.clean(sourceDocument.body().html(), "", SAFELIST, OUTPUT_SETTINGS);
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings(OUTPUT_SETTINGS);
        for (Element image : document.select("img[data-kb-src]"))
        {
            image.attr("src", image.attr("data-kb-src"));
            image.removeAttr("data-kb-src");
        }
        for (Element paragraph : document.select("p"))
        {
            if (StringUtils.isBlank(paragraph.text()) && paragraph.selectFirst("img") == null)
            {
                paragraph.remove();
            }
        }
        removeRootWhitespaceBetweenBlocks(document);
        for (Element link : document.select("a[href]"))
        {
            link.attr("rel", "noopener noreferrer");
            if ("_blank".equalsIgnoreCase(link.attr("target")))
            {
                link.attr("target", "_blank");
            }
            else
            {
                link.removeAttr("target");
            }
        }
        return document.body().html().trim();
    }

    private void removeRootWhitespaceBetweenBlocks(Document document)
    {
        for (Node node : new ArrayList<>(document.body().childNodes()))
        {
            if (!(node instanceof TextNode textNode) || !StringUtils.isBlank(textNode.getWholeText()))
            {
                continue;
            }
            Node previous = nearestContentSibling(node, true);
            Node next = nearestContentSibling(node, false);
            if (previous instanceof Element previousElement && next instanceof Element nextElement
                && ROOT_BLOCK_TAGS.contains(previousElement.tagName())
                && ROOT_BLOCK_TAGS.contains(nextElement.tagName()))
            {
                node.remove();
            }
        }
    }

    private Node nearestContentSibling(Node node, boolean previous)
    {
        Node sibling = previous ? node.previousSibling() : node.nextSibling();
        while (sibling instanceof TextNode textNode && StringUtils.isBlank(textNode.getWholeText()))
        {
            sibling = previous ? sibling.previousSibling() : sibling.nextSibling();
        }
        return sibling;
    }
}
