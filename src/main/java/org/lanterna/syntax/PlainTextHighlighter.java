package org.lanterna.syntax;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

/**
 * Fallback highlighter for unhandled extensions or raw text files.
 */
public class PlainTextHighlighter implements SyntaxHighlighter {

    @Override
    public boolean supportsExtension(String extension) {
        return true;
    }

    @Override
    public HighlightState drawLine(TextGraphics tg, int x, int y, String line, int maxWidth, HighlightState state) {
        int length = Math.min(line.length(), maxWidth);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        tg.putString(x, y, line.substring(0, length));
        return HighlightState.NORMAL;
    }
}