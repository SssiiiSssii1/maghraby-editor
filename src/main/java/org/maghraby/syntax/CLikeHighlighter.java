package org.maghraby.syntax;

import java.util.Set;

/**
 * Syntax highlighter for C-family languages (Java, C, C++, etc.) using ANSI escape codes.
 */
public class CLikeHighlighter implements SyntaxHighlighter {

    private final Set<String> supportedExtensions;
    private final Set<String> keywords;

    // ثوابت ألوان ANSI كبديل لـ TextColor في Lanterna
    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_GREEN = "\033[38;5;108m";
    private static final String ANSI_YELLOW = "\033[38;5;180m";
    private static final String ANSI_MAGENTA = "\033[38;5;176m";
    private static final String ANSI_CYAN = "\033[38;5;75m";
    private static final String ANSI_BLUE = "\033[38;5;111m";
    private static final String ANSI_RED = "\033[38;5;204m";

    public CLikeHighlighter(Set<String> supportedExtensions, Set<String> keywords) {
        this.supportedExtensions = supportedExtensions;
        this.keywords = keywords;
    }

    @Override
    public boolean supportsExtension(String extension) {
        return supportedExtensions.contains(extension.toLowerCase());
    }

    @Override
    public HighlightResult highlightLine(String line, HighlightState state) {
        StringBuilder resultLine = new StringBuilder();
        int i = 0;
        boolean inBlock = (state == HighlightState.C_BLOCK_COMMENT);
        String currentColor = ANSI_RESET;

        while (i < line.length()) {
            char c = line.charAt(i);
            String token;
            String newColor;
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
                newColor = ANSI_GREEN;
            }

            // 2. Single-line comment (//)
            else if (line.startsWith("//", i)) {
                token = line.substring(i);
                tokenLength = token.length();
                newColor = ANSI_GREEN;
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
                newColor = ANSI_GREEN;
            }

            // 4. Preprocessor directives
            else if (c == '#' && (supportedExtensions.contains("c") || supportedExtensions.contains("cpp") || supportedExtensions.contains("h"))) {
                int end = i + 1;
                while (end < line.length() && Character.isLetterOrDigit(line.charAt(end))) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                newColor = ANSI_MAGENTA;
            }

            // 5. Annotations (@Override)
            else if (c == '@' && Character.isJavaIdentifierStart(peekChar(line, i + 1))) {
                int end = i + 1;
                while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                newColor = ANSI_YELLOW;
            }

            // 6. String and character literals
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
                newColor = ANSI_YELLOW;
            }

            // 7. Numeric literals
            else if (Character.isDigit(c) || (c == '.' && Character.isDigit(peekChar(line, i + 1)))) {
                int end = i + 1;
                boolean isHex = (c == '0' && end < line.length() && (line.charAt(end) == 'x' || line.charAt(end) == 'X'));
                if (isHex) end++;

                while (end < line.length()) {
                    char next = line.charAt(end);
                    if (isHex ? isHexDigit(next) : Character.isDigit(next)) {
                        end++;
                    } else if (next == '.' && Character.isDigit(peekChar(line, end + 1))) {
                        end++;
                    } else if (!isHex && (next == 'e' || next == 'E')
                            && (Character.isDigit(peekChar(line, end + 1))
                            || ((peekChar(line, end + 1) == '+' || peekChar(line, end + 1) == '-')
                            && Character.isDigit(peekChar(line, end + 2))))) {
                        end += 2; // exponent such as 1e9 or 2.5e-3
                    } else if (next == 'f' || next == 'F' || next == 'l' || next == 'L' || next == 'd' || next == 'D'
                            || next == 'u' || next == 'U' || next == '_') {
                        end++;
                    } else {
                        break;
                    }
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                newColor = ANSI_MAGENTA;
            }

            // 8. Identifiers and keywords
            else if (Character.isJavaIdentifierStart(c)) {
                int end = i + 1;
                while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();

                if (keywords.contains(token)) {
                    newColor = ANSI_CYAN;
                } else if (Character.isUpperCase(token.charAt(0))) {
                    newColor = ANSI_YELLOW;
                } else {
                    int peek = end;
                    while (peek < line.length() && Character.isWhitespace(line.charAt(peek))) {
                        peek++;
                    }
                    if (peek < line.length() && line.charAt(peek) == '(') {
                        newColor = ANSI_BLUE;
                    } else {
                        newColor = ANSI_RESET;
                    }
                }
            }

            // 9. Operators
            else if ("=+-*/%&|^!<>?:".indexOf(c) != -1) {
                token = String.valueOf(c);
                tokenLength = 1;
                newColor = ANSI_RED;
            }

            // 10. Delimiters
            else {
                token = String.valueOf(c);
                tokenLength = 1;
                newColor = ANSI_RESET;
            }

            // إضافة اللون فقط إذا تغير لتجنب تكرار رموز ANSI غير الضرورية (Optimization)
            if (!newColor.equals(currentColor)) {
                resultLine.append(newColor);
                currentColor = newColor;
            }
            resultLine.append(token);

            i += tokenLength;
        }

        // إغلاق أي لون مفتوح في نهاية السطر
        if (!currentColor.equals(ANSI_RESET)) {
            resultLine.append(ANSI_RESET);
        }

        HighlightState nextState = inBlock ? HighlightState.C_BLOCK_COMMENT : HighlightState.NORMAL;
        return new HighlightResult(resultLine.toString(), nextState);
    }

    private char peekChar(String line, int index) {
        return index < line.length() ? line.charAt(index) : '\0';
    }

    private boolean isHexDigit(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}