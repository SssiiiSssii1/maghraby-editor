package org.maghraby.syntax;

/**
 * Fallback highlighter for unhandled extensions or raw text files.
 */
public class PlainTextHighlighter implements SyntaxHighlighter {

    @Override
    public boolean supportsExtension(String extension) {
        return true;
    }

    @Override
    public HighlightResult highlightLine(String line, HighlightState state) {
        // لا يوجد تلوين، نرجع النص كما هو مع حالة عادية
        return new HighlightResult(line, HighlightState.NORMAL);
    }
}