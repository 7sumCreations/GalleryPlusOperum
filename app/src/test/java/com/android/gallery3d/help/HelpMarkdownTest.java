package com.android.gallery3d.help;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The block and inline parsing of the tiny help renderer, on the plain JVM. */
public class HelpMarkdownTest {

    @Test
    public void headingsBulletsAndParagraphsAreSplitIntoBlocks() {
        List<HelpMarkdown.Block> blocks = HelpMarkdown.parse(
                "# Title\n\nIntro line one\nline two\n\n## Section\n\n- first\n- second\n"
                        + "  continued\n\nAfter.");

        assertEquals("[HEADING1:Title, PARAGRAPH:Intro line one line two, HEADING2:Section,"
                + " BULLET:first, BULLET:second continued, PARAGRAPH:After.]",
                blocks.toString());
    }

    @Test
    public void deeperHeadingsReadAsSectionHeadingsAndEmptyOnesAreDropped() {
        List<HelpMarkdown.Block> blocks = HelpMarkdown.parse("### Deep\n#\n* star bullet");
        assertEquals("[HEADING2:Deep, BULLET:star bullet]", blocks.toString());
    }

    @Test
    public void nullBlankAndWindowsLineEndingsAreHandled() {
        assertTrue(HelpMarkdown.parse(null).isEmpty());
        assertTrue(HelpMarkdown.parse("  \n\n").isEmpty());
        assertEquals("[HEADING1:A, PARAGRAPH:b c]",
                HelpMarkdown.parse("# A\r\n\r\nb\r\nc").toString());
    }

    @Test
    public void boldIsRemovedAndRecordedAsRanges() {
        HelpMarkdown.Inline inline = HelpMarkdown.inline("Tap **Move** or **Copy to folder**.");
        assertEquals("Tap Move or Copy to folder.", inline.text);
        assertEquals(2, inline.boldRanges.size());
        assertArrayEquals(new int[] {4, 8}, inline.boldRanges.get(0));
        assertArrayEquals(new int[] {12, 26}, inline.boldRanges.get(1));
    }

    @Test
    public void unmatchedAsterisksStayLiteral() {
        HelpMarkdown.Inline lone = HelpMarkdown.inline("5 ** 2 has no partner");
        assertEquals("5 ** 2 has no partner", lone.text);
        assertTrue(lone.boldRanges.isEmpty());

        HelpMarkdown.Inline empty = HelpMarkdown.inline("empty **** pair");
        assertEquals("empty **** pair", empty.text);
        assertTrue(empty.boldRanges.isEmpty());
    }

    @Test
    public void linksBecomePlainText() {
        assertEquals("the roadmap (ROADMAP.md) and https://example.org/x",
                HelpMarkdown.inline("[the roadmap](ROADMAP.md) and <https://example.org/x>").text);
        assertEquals("https://example.org",
                HelpMarkdown.inline("[https://example.org](https://example.org)").text);
        // Malformed link syntax is left alone rather than eaten.
        assertEquals("[not a link] and <b>", HelpMarkdown.inline("[not a link] and <b>").text);
    }
}
