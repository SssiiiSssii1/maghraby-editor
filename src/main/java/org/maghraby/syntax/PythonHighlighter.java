package org.maghraby.syntax;

import java.util.Set;

/**
 * Syntax highlighter for Python source code using ANSI escape codes.
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

    // ثوابت ألوان ANSI
    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_GREEN = "\033[32m";
    private static final String ANSI_YELLOW = "\033[33m";
    private static final String ANSI_MAGENTA = "\033[35m";
    private static final String ANSI_CYAN = "\033[36m";
    private static final String ANSI_BLUE = "\033[34m";
    private static final String ANSI_RED = "\033[31m";

    @Override
    public boolean supportsExtension(String extension) {
        return "py".equalsIgnoreCase(extension);
    }

    @Override
    public HighlightResult highlightLine(String line, HighlightState state) {
        StringBuilder resultLine = new StringBuilder();
        int i = 0;
        String currentColor = ANSI_RESET;
        HighlightState nextState = state;

        while (i < line.length()) {
            char c = line.charAt(i);
            String token;
            String newColor;
            int tokenLength;

            // 1. Ongoing multi-line docstring continuation
            if (nextState == HighlightState.PY_TRIPLE_DOUBLE || nextState == HighlightState.PY_TRIPLE_SINGLE) {
                String closingQuote = (nextState == HighlightState.PY_TRIPLE_DOUBLE) ? "\"\"\"" : "'''";
                int end = line.indexOf(closingQuote, i);

                if (end == -1) {
                    token = line.substring(i);
                } else {
                    token = line.substring(i, end + 3);
                    nextState = HighlightState.NORMAL;
                }
                tokenLength = token.length();
                newColor = ANSI_YELLOW;
            }

            // 2. Comments (#)
            else if (c == '#') {
                token = line.substring(i);
                tokenLength = token.length();
                newColor = ANSI_GREEN;
            }

            // 3. New multi-line docstring openings
            else if (line.startsWith("\"\"\"", i)) {
                int end = line.indexOf("\"\"\"", i + 3);
                if (end == -1) {
                    token = line.substring(i);
                    nextState = HighlightState.PY_TRIPLE_DOUBLE;
                } else {
                    token = line.substring(i, end + 3);
                }
                tokenLength = token.length();
                newColor = ANSI_YELLOW;
            }
            else if (line.startsWith("'''", i)) {
                int end = line.indexOf("'''", i + 3);
                if (end == -1) {
                    token = line.substring(i);
                    nextState = HighlightState.PY_TRIPLE_SINGLE;
                } else {
                    token = line.substring(i, end + 3);
                }
                tokenLength = token.length();
                newColor = ANSI_YELLOW;
            }

            // 4. Decorators (@decorator_name)
            else if (c == '@') {
                int end = i + 1;
                while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_')) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                newColor = ANSI_YELLOW;
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
                newColor = ANSI_YELLOW;
            }

            // 6. Numbers (integers, floats, underscores)
            else if (Character.isDigit(c)) {
                int end = i + 1;
                while (end < line.length()) {
                    char next = line.charAt(end);
                    if (Character.isDigit(next) || next == '_') {
                        end++;
                    } else if (next == '.' && end + 1 < line.length() && Character.isDigit(line.charAt(end + 1))) {
                        end++;
                    } else {
                        break;
                    }
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                newColor = ANSI_MAGENTA;
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
                    newColor = ANSI_CYAN;
                } else if (BUILTINS.contains(token)) {
                    newColor = ANSI_GREEN;
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

            // 8. Operators
            else if ("=+-*/%&|^!<>".indexOf(c) != -1) {
                token = String.valueOf(c);
                tokenLength = 1;
                newColor = ANSI_RED;
            }

            // 9. Delimiters and whitespace
            else {
                token = String.valueOf(c);
                tokenLength = 1;
                newColor = ANSI_RESET;
            }

            // إضافة اللون
            if (!newColor.equals(currentColor)) {
                resultLine.append(newColor);
                currentColor = newColor;
            }
            resultLine.append(token);

            i += tokenLength;
        }

        // إغلاق أي لون مفتوح
        if (!currentColor.equals(ANSI_RESET)) {
            resultLine.append(ANSI_RESET);
        }

        return new HighlightResult(resultLine.toString(), nextState);
    }
}