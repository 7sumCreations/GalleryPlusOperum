/*
 * Copyright (C) 2026 The GrapheneGallery Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.help;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A deliberately tiny Markdown renderer for HELP.md: "#" and "##" headings,
 * "- " bullets, **bold**, and paragraphs. Nothing else is interpreted. Links
 * become plain text ("text (url)"), so nothing in the help is clickable and
 * nothing ever reaches for the network.
 *
 * parse() and inline() are plain Java, so the structure is testable on the JVM
 * without Android; render() turns the result into Spannables.
 */
public final class HelpMarkdown {

    public enum Kind { HEADING1, HEADING2, BULLET, PARAGRAPH }

    /** One block of the document: its kind and its raw (still marked-up) text. */
    public static final class Block {
        public final Kind kind;
        public final String text;

        Block(Kind kind, String text) {
            this.kind = kind;
            this.text = text;
        }

        @Override
        public String toString() {
            return kind + ":" + text;
        }
    }

    /** Text with its markup removed, and the [start, end) ranges that were bold. */
    public static final class Inline {
        public final String text;
        public final List<int[]> boldRanges;

        Inline(String text, List<int[]> boldRanges) {
            this.text = text;
            this.boldRanges = Collections.unmodifiableList(boldRanges);
        }
    }

    static final float HEADING1_SCALE = 1.4f;
    static final float HEADING2_SCALE = 1.15f;

    private HelpMarkdown() {
    }

    /** Splits the document into blocks. Never throws; null reads as empty. */
    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<Block>();
        if (markdown == null) return blocks;
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        Kind openKind = null;
        StringBuilder open = new StringBuilder();
        for (String rawLine : lines) {
            String line = stripTrailing(rawLine);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                openKind = flush(blocks, openKind, open);
                continue;
            }
            if (trimmed.startsWith("#")) {
                openKind = flush(blocks, openKind, open);
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                String title = trimmed.substring(level).trim();
                if (!title.isEmpty()) {
                    blocks.add(new Block(level == 1 ? Kind.HEADING1 : Kind.HEADING2, title));
                }
                continue;
            }
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                openKind = flush(blocks, openKind, open);
                openKind = Kind.BULLET;
                open.append(trimmed.substring(2).trim());
                continue;
            }
            // Plain text: continues the open bullet or paragraph, or starts one.
            if (openKind == null) openKind = Kind.PARAGRAPH;
            if (open.length() > 0) open.append(' ');
            open.append(trimmed);
        }
        flush(blocks, openKind, open);
        return blocks;
    }

    /**
     * Removes inline markup: **bold** is recorded as a range, [text](url)
     * becomes "text (url)", and &lt;url&gt; becomes url. An unmatched "**" is
     * left as it is.
     */
    public static Inline inline(String raw) {
        String text = plainLinks(raw == null ? "" : raw);
        StringBuilder out = new StringBuilder(text.length());
        List<int[]> bold = new ArrayList<int[]>();
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf("**", i);
            if (open < 0) {
                out.append(text, i, text.length());
                break;
            }
            int close = text.indexOf("**", open + 2);
            if (close < 0 || close == open + 2) {
                // No partner (or "****"): keep the asterisks literally.
                out.append(text, i, open + 2);
                i = open + 2;
                continue;
            }
            out.append(text, i, open);
            int start = out.length();
            out.append(text, open + 2, close);
            bold.add(new int[] {start, out.length()});
            i = close + 2;
        }
        return new Inline(out.toString(), bold);
    }

    /** The whole document as styled text for a TextView. */
    public static CharSequence render(String markdown, int bulletGapPx) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        Kind previous = null;
        for (Block block : parse(markdown)) {
            if (previous != null) {
                // Bullets in one list sit on consecutive lines; everything
                // else is separated by a blank line.
                out.append(previous == Kind.BULLET && block.kind == Kind.BULLET ? "\n" : "\n\n");
            }
            Inline inline = inline(block.text);
            int start = out.length();
            out.append(inline.text);
            int end = out.length();
            for (int[] range : inline.boldRanges) {
                out.setSpan(new StyleSpan(Typeface.BOLD), start + range[0], start + range[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            switch (block.kind) {
                case HEADING1:
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new RelativeSizeSpan(HEADING1_SCALE), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case HEADING2:
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new RelativeSizeSpan(HEADING2_SCALE), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case BULLET:
                    out.setSpan(new BulletSpan(bulletGapPx), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case PARAGRAPH:
                default:
                    break;
            }
            previous = block.kind;
        }
        return out;
    }

    private static Kind flush(List<Block> blocks, Kind kind, StringBuilder text) {
        if (kind != null && text.length() > 0) {
            blocks.add(new Block(kind, text.toString()));
        }
        text.setLength(0);
        return null;
    }

    /** [text](url) -> "text (url)"; <url> -> url. Anything malformed is left alone. */
    static String plainLinks(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '[') {
                int closeText = text.indexOf(']', i + 1);
                if (closeText > i && closeText + 1 < text.length()
                        && text.charAt(closeText + 1) == '(') {
                    int closeUrl = text.indexOf(')', closeText + 2);
                    if (closeUrl > closeText) {
                        String label = text.substring(i + 1, closeText);
                        String url = text.substring(closeText + 2, closeUrl).trim();
                        out.append(label);
                        if (!url.isEmpty() && !url.equals(label)) {
                            out.append(" (").append(url).append(')');
                        }
                        i = closeUrl + 1;
                        continue;
                    }
                }
            } else if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close > i) {
                    String inner = text.substring(i + 1, close);
                    if (inner.startsWith("http://") || inner.startsWith("https://")) {
                        out.append(inner);
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        return line.substring(0, end);
    }
}
