package org.lanterna.syntax;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

import java.util.Set;

/**
 * Syntax highlighter for Python source code.
 */
public class PythonHighlighter implements SyntaxHighlighter {

    private static final Set<String> KEYWORDS = Set.of(
            "and", "as", "assert", "async", "await", "break", "class", "continue",
            "def", "del", "elif", "else", "except", "False", "finally", "for",
            "from", "global", "if", "import", "in", "is", "lambda", "None",
            "nonlocal", "not", "or", "pass", "raise", "return", "True", "try",
            "while", "with", "yield"
    );

    private static final Set<String> BUILTINS = Set.of(
            "print", "len", "range", "int", "str", "float", "list", "dict", "set",
            "tuple", "super", "isinstance", "type", "open", "enumerate", "zip"
    );

    @Override
    public boolean supportsExtension(String extension) {
        return "py".equalsIgnoreCase(extension);
    }

    @Override
    public HighlightState drawLine(TextGraphics tg, int x, int y, String line, int maxWidth, HighlightState state) {
        int i = 0;
        int drawn = 0;

        while (i < line.length()) {
            char c = line.charAt(i);
            String token;
            TextColor color;
            int tokenLength;

            // 1. Ongoing multi-line docstring continuation with strict delimiter matching
            if (state == HighlightState.PY_TRIPLE_DOUBLE || state == HighlightState.PY_TRIPLE_SINGLE) {
                String closingQuote = (state == HighlightState.PY_TRIPLE_DOUBLE) ? "\"\"\"" : "'''";
                int end = line.indexOf(closingQuote, i);

                if (end == -1) {
                    token = line.substring(i);
                } else {
                    token = line.substring(i, end + 3);
                    state = HighlightState.NORMAL;
                }
                tokenLength = token.length();
                color = TextColor.ANSI.YELLOW;
            }

            // 2. Comments (#)
            else if (c == '#') {
                token = line.substring(i);
                tokenLength = token.length();
                color = TextColor.ANSI.GREEN;
            }

            // 3. New multi-line docstring openings
            else if (line.startsWith("\"\"\"", i)) {
                int end = line.indexOf("\"\"\"", i + 3);
                if (end == -1) {
                    token = line.substring(i);
                    state = HighlightState.PY_TRIPLE_DOUBLE;
                } else {
                    token = line.substring(i, end + 3);
                }
                tokenLength = token.length();
                color = TextColor.ANSI.YELLOW;
            }
            else if (line.startsWith("'''", i)) {
                int end = line.indexOf("'''", i + 3);
                if (end == -1) {
                    token = line.substring(i);
                    state = HighlightState.PY_TRIPLE_SINGLE;
                } else {
                    token = line.substring(i, end + 3);
                }
                tokenLength = token.length();
                color = TextColor.ANSI.YELLOW;
            }

            // 4. Decorators (@decorator_name)
            else if (c == '@') {
                int end = i + 1;
                while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_')) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                color = TextColor.ANSI.YELLOW;
            }

            // 5. Standard single-line string literals
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

            // 6. Numbers (integers, floats, underscores)
            else if (Character.isDigit(c)) {
                int end = i + 1;
                while (end < line.length()) {
                    char next = line.charAt(end);
                    if (Character.isDigit(next) || next == '_') {
                        end++;
                    } else if (next == '.' && end + 1 < line.length() && Character.isDigit(line.charAt(end + 1))) {
                        end++; // Accept decimal dot only if followed by digits
                    } else {
                        break;
                    }
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                color = TextColor.ANSI.MAGENTA;
            }

            // 7. Identifiers, keywords, built-ins, and function calls
            else if (Character.isLetter(c) || c == '_') {
                int end = i + 1;
                while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_')) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();

                if (KEYWORDS.contains(token)) {
                    color = TextColor.ANSI.CYAN;
                } else if (BUILTINS.contains(token)) {
                    color = TextColor.ANSI.GREEN;
                } else {
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

            // 8. Operators
            else if ("=+-*/%&|^!<>".indexOf(c) != -1) {
                token = String.valueOf(c);
                tokenLength = 1;
                color = TextColor.ANSI.RED;
            }

            // 9. Delimiters and whitespace
            else {
                token = String.valueOf(c);
                tokenLength = 1;
                color = TextColor.ANSI.DEFAULT;
            }

            // Clip rendering to maxWidth without truncating parser state
            if (drawn < maxWidth) {
                int remaining = maxWidth - drawn;
                String printable = (token.length() > remaining) ? token.substring(0, remaining) : token;
                tg.setForegroundColor(color);
                tg.putString(x + drawn, y, printable);
                drawn += printable.length();
            }

            i += tokenLength;
        }

        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        return state;
    }
}