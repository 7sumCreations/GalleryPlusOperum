package com.android.gallery3d.help;

import static org.junit.Assert.assertEquals;

import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** What the TextView actually receives: plain text plus the expected spans. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HelpMarkdownRenderTest {

    private static final String DOC =
            "# Help\n\nTap **Move**.\n\n## Trash\n\n- one\n- two\n\nEnd.";

    @Test
    public void rendersPlainTextWithBlankLinesBetweenBlocks() {
        CharSequence out = HelpMarkdown.render(DOC, 8);
        assertEquals("Help\n\nTap Move.\n\nTrash\n\none\ntwo\n\nEnd.", out.toString());
    }

    @Test
    public void headingsAreBoldAndLargerAndBulletsGetBulletSpans() {
        Spanned out = (Spanned) HelpMarkdown.render(DOC, 8);
        String text = out.toString();

        RelativeSizeSpan[] sizes = out.getSpans(0, out.length(), RelativeSizeSpan.class);
        assertEquals(2, sizes.length);
        assertEquals("Help", text.substring(out.getSpanStart(sizes[0]), out.getSpanEnd(sizes[0])));
        assertEquals(HelpMarkdown.HEADING1_SCALE, sizes[0].getSizeChange(), 0f);
        assertEquals("Trash", text.substring(out.getSpanStart(sizes[1]), out.getSpanEnd(sizes[1])));
        assertEquals(HelpMarkdown.HEADING2_SCALE, sizes[1].getSizeChange(), 0f);

        StyleSpan[] bold = out.getSpans(0, out.length(), StyleSpan.class);
        assertEquals(3, bold.length);
        for (StyleSpan span : bold) assertEquals(Typeface.BOLD, span.getStyle());
        int moveAt = text.indexOf("Move");
        assertEquals(1, out.getSpans(moveAt, moveAt + 4, StyleSpan.class).length);

        BulletSpan[] bullets = out.getSpans(0, out.length(), BulletSpan.class);
        assertEquals(2, bullets.length);
        assertEquals("one", text.substring(out.getSpanStart(bullets[0]), out.getSpanEnd(bullets[0])));
        assertEquals("two", text.substring(out.getSpanStart(bullets[1]), out.getSpanEnd(bullets[1])));
    }

    @Test
    public void emptyDocumentRendersAsEmptyText() {
        assertEquals("", HelpMarkdown.render("", 8).toString());
        assertEquals("", HelpMarkdown.render(null, 8).toString());
    }
}
