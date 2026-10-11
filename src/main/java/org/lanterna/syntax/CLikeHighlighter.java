package org.lanterna.syntax;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

import java.util.Set;

/**
 * Tokenizer and renderer for C-family languages (Java, C, C++, etc.).
 * Handles single-line comments, multi-line block comments, strings, numbers, and keywords.
 */
public class CLikeHighlighter implements SyntaxHighlighter {

    private final Set<String> supportedExtensions;
    private final Set<String> keywords;

    public CLikeHighlighter(Set<String> supportedExtensions, Set<String> keywords) {
        this.supportedExtensions = supportedExtensions;
        this.keywords = keywords;
    }

    @Override
    public boolean supportsExtension(String extension) {
        return supportedExtensions.contains(extension.toLowerCase());
    }

    @Override
    public boolean drawLine(TextGraphics tg, int x, int y, String line, int maxWidth, boolean inBlockComment) {
        int i = 0;
        int drawn = 0;

        while (i < line.length() && drawn < maxWidth) {
            char c = line.charAt(i);
            String token;
            TextColor color;
            int tokenLength;

            // 1. Ongoing multi-line block comment continuation
            if (inBlockComment) {
                int end = line.indexOf("*/", i);
                if (end == -1) {
                    token = line.substring(i);
                    inBlockComment = true;
                } else {
                    token = line.substring(i, end + 2);
                    inBlockComment = false;
                }
                tokenLength = token.length();
                color = TextColor.ANSI.GREEN;
            }

            // 2. Single-line comment (//)
            else if (line.startsWith("//", i)) {
                token = line.substring(i);
                tokenLength = token.length();
                color = TextColor.ANSI.GREEN;
            }

            // 3. New multi-line block comment (/*)
            else if (line.startsWith("/*", i)) {
                int end = line.indexOf("*/", i + 2);
                if (end == -1) {
                    token = line.substring(i);
                    inBlockComment = true;
                } else {
                    token = line.substring(i, end + 2);
                }
                tokenLength = token.length();
                color = TextColor.ANSI.GREEN;
            }

            // 4. String and character literals with backslash escape handling
            else if (c == '"' || c == '\'') {
                char quote = c;
                int end = i + 1;
                boolean escaped = false;

                while (end < line.length()) {
                    char next = line.charAt(end++);
                    if (escaped) {
                        escaped = false;
                    } else if (next == '\\') {
                        escaped = true;
                    } else if (next == quote) {
                        break;
                    }
                }

                token = line.substring(i, end);
                tokenLength = token.length();
                color = TextColor.ANSI.YELLOW;
            }

            // 5. Numeric literals
            else if (Character.isDigit(c)) {
                int end = i + 1;
                while (end < line.length()) {
                    char next = line.charAt(end);
                    if (Character.isLetterOrDigit(next) || next == '.' || next == '_') {
                        end++;
                    } else {
                        break;
                    }
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                color = TextColor.ANSI.MAGENTA;
            }

            // 6. Identifiers, keywords, and function calls
            else if (Character.isLetter(c) || c == '_') {
                int end = i + 1;
                while (end < line.length()) {
                    char next = line.charAt(end);
                    if (Character.isLetterOrDigit(next) || next == '_') {
                        end++;
                    } else {
                        break;
                    }
                }
                token = line.substring(i, end);
                tokenLength = token.length();

                if (keywords.contains(token)) {
                    color = TextColor.ANSI.CYAN;
                } else {
                    // Check if identifier is immediately followed by '('
                    int peek = end;
                    while (peek < line.length() && Character.isWhitespace(line.charAt(peek))) {
                        peek++;
                    }
                    if (peek < line.length() && line.charAt(peek) == '(') {
                        color = TextColor.ANSI.BLUE;
                    } else {
                        color = TextColor.ANSI.DEFAULT;
                    }
                }
            }

            // 7. Operators, punctuation, and whitespace
            else {
                token = String.valueOf(c);
                tokenLength = 1;
                color = TextColor.ANSI.DEFAULT;
            }

            // Clip token if it exceeds the remaining line width
            int remaining = maxWidth - drawn;
            String printableToken = (token.length() > remaining) ? token.substring(0, remaining) : token;

            tg.setForegroundColor(color);
            tg.putString(x + drawn, y, printableToken);

            drawn += printableToken.length();
            i += tokenLength; // Always advance by true unclipped token length
        }

        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        return inBlockComment;
    }
}