package org.lanterna.syntax;

import com.googlecode.lanterna.graphics.TextGraphics;

/**
 * Strategy interface defining how a single line is tokenized, styled, and rendered.
 */
public interface SyntaxHighlighter {

    /**
     * Renders a single line with syntax highlighting while tracking multi-line state.
     *
     * @param tg       Lanterna text graphics target.
     * @param x        Terminal column offset.
     * @param y        Terminal row index.
     * @param line     The raw line text.
     * @param maxWidth Maximum displayable terminal columns.
     * @param state    Parser state at the start of this line.
     * @return Updated parser state at the end of this line.
     */
    HighlightState drawLine(TextGraphics tg, int x, int y, String line, int maxWidth, HighlightState state);

    /**
     * Checks if this highlighter supports the specified file extension.
     */
    boolean supportsExtension(String extension);
}