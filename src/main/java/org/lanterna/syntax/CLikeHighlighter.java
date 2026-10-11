package org.lanterna.syntax;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

import java.util.Set;

/**
 * Syntax highlighter for C-family languages (Java, C, C++, etc.).
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
    public HighlightState drawLine(TextGraphics tg, int x, int y, String line, int maxWidth, HighlightState state) {
        int i = 0;
        int drawn = 0;
        boolean inBlock = (state == HighlightState.C_BLOCK_COMMENT);

        while (i < line.length()) {
            char c = line.charAt(i);
            String token;
            TextColor color;
            int tokenLength;

            // 1. Ongoing multi-line block comment continuation (/* ... */)
            if (inBlock) {
                int end = line.indexOf("*/", i);
                if (end == -1) {
                    token = line.substring(i);
                    inBlock = true;
                } else {
                    token = line.substring(i, end + 2);
                    inBlock = false;
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

            // 3. New multi-line block comment opening (/*)
            else if (line.startsWith("/*", i)) {
                int end = line.indexOf("*/", i + 2);
                if (end == -1) {
                    token = line.substring(i);
                    inBlock = true;
                } else {
                    token = line.substring(i, end + 2);
                }
                tokenLength = token.length();
                color = TextColor.ANSI.GREEN;
            }

            // 4. Preprocessor directives for C/C++ (#include, #define, #pragma)
            else if (c == '#' && (supportedExtensions.contains("c") || supportedExtensions.contains("cpp") || supportedExtensions.contains("h"))) {
                int end = i + 1;
                while (end < line.length() && Character.isLetterOrDigit(line.charAt(end))) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                color = TextColor.ANSI.MAGENTA;
            }

            // 5. Annotations (e.g., @Override, @Deprecated)
            else if (c == '@' && Character.isJavaIdentifierStart(peekChar(line, i + 1))) {
                int end = i + 1;
                while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                color = TextColor.ANSI.YELLOW;
            }

            // 6. String and character literals with backslash escape handling
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

            // 7. Numeric literals (Hex, Decimal, Float; prevents swallowing chained method calls)
            else if (Character.isDigit(c) || (c == '.' && Character.isDigit(peekChar(line, i + 1)))) {
                int end = i + 1;
                boolean isHex = (c == '0' && end < line.length() && (line.charAt(end) == 'x' || line.charAt(end) == 'X'));
                if (isHex) end++;

                while (end < line.length()) {
                    char next = line.charAt(end);
                    if (isHex ? isHexDigit(next) : Character.isDigit(next)) {
                        end++;
                    } else if (next == '.' && Character.isDigit(peekChar(line, end + 1))) {
                        end++; // Only accept '.' as part of the number if followed by digits
                    } else if (next == 'f' || next == 'F' || next == 'l' || next == 'L' || next == 'd' || next == 'D' || next == '_') {
                        end++;
                    } else {
                        break;
                    }
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                color = TextColor.ANSI.MAGENTA;
            }

            // 8. Identifiers, keywords, PascalCase types, and method invocations
            else if (Character.isJavaIdentifierStart(c)) {
                int end = i + 1;
                while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();

                if (keywords.contains(token)) {
                    color = TextColor.ANSI.CYAN;
                } else if (Character.isUpperCase(token.charAt(0))) {
                    color = TextColor.ANSI.YELLOW; // Highlight standard class / struct names
                } else {
                    int peek = end;
                    while (peek < line.length() && Character.isWhitespace(line.charAt(peek))) {
                        peek++;
                    }
                    if (peek < line.length() && line.charAt(peek) == '(') {
                        color = TextColor.ANSI.BLUE; // Highlight function / method call
                    } else {
                        color = TextColor.ANSI.DEFAULT;
                    }
                }
            }

            // 9. Operators
            else if ("=+-*/%&|^!<>?:".indexOf(c) != -1) {
                token = String.valueOf(c);
                tokenLength = 1;
                color = TextColor.ANSI.RED;
            }

            // 10. Delimiters, punctuation, and whitespace
            else {
                token = String.valueOf(c);
                tokenLength = 1;
                color = TextColor.ANSI.DEFAULT;
            }

            // Render only within terminal bounds; parsing continues to preserve line state
            if (drawn < maxWidth) {
                int remaining = maxWidth - drawn;
                String printableToken = (token.length() > remaining) ? token.substring(0, remaining) : token;
                tg.setForegroundColor(color);
                tg.putString(x + drawn, y, printableToken);
                drawn += printableToken.length();
            }

            i += tokenLength;
        }

        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        return inBlock ? HighlightState.C_BLOCK_COMMENT : HighlightState.NORMAL;
    }

    private char peekChar(String line, int index) {
        return index < line.length() ? line.charAt(index) : '\0';
    }

    private boolean isHexDigit(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}