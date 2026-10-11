package org.lanterna.syntax;

import com.googlecode.lanterna.graphics.TextGraphics;

/**
 * Strategy interface defining how a single line of text is tokenized, colored, and drawn.
 */
public interface SyntaxHighlighter {

    /**
     * Renders a single line to the terminal screen with syntax styling.
     *
     * @param tg             The Lanterna graphics target.
     * @param x              Terminal column offset where rendering starts.
     * @param y              Terminal row index to draw on.
     * @param line           The source code string of the line.
     * @param maxWidth       Maximum terminal width available for this line.
     * @param inBlockComment Current multi-line block comment state entering this line.
     * @return The updated block comment state exiting this line (true if still open).
     */
    boolean drawLine(TextGraphics tg, int x, int y, String line, int maxWidth, boolean inBlockComment);

    /**
     * Checks if this highlighter supports the given file extension (e.g., "java", "cpp").
     *
     * @param extension File extension without the leading dot.
     * @return true if supported, false otherwise.
     */
    boolean supportsExtension(String extension);
}