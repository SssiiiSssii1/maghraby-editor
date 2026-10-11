package org.lanterna.syntax;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

import java.util.Set;

/**
 * Tokenizer and renderer for Python files, handling '#' comments,
 * standard strings, triple-quoted docstrings, and Python keywords.
 */
public class PythonHighlighter implements SyntaxHighlighter {

    private static final Set<String> KEYWORDS = Set.of(
            "and", "as", "assert", "async", "await", "break", "class", "continue",
            "def", "del", "elif", "else", "except", "False", "finally", "for",
            "from", "global", "if", "import", "in", "is", "lambda", "None",
            "nonlocal", "not", "or", "pass", "raise", "return", "True", "try",
            "while", "with", "yield"
    );

    @Override
    public boolean supportsExtension(String extension) {
        return "py".equalsIgnoreCase(extension);
    }

    @Override
    public boolean drawLine(TextGraphics tg, int x, int y, String line, int maxWidth, boolean inDocstring) {
        int i = 0;
        int drawn = 0;

        while (i < line.length() && drawn < maxWidth) {
            char c = line.charAt(i);
            String token;
            TextColor color;
            int tokenLength;

            // 1. Ongoing multi-line docstring continuation (""" or ''')
            if (inDocstring) {
                int end = line.indexOf("\"\"\"", i);
                if (end == -1) {
                    end = line.indexOf("'''", i);
                }

                if (end == -1) {
                    token = line.substring(i);
                    inDocstring = true;
                } else {
                    token = line.substring(i, end + 3);
                    inDocstring = false;
                }
                tokenLength = token.length();
                color = TextColor.ANSI.YELLOW;
            }

            // 2. Python comments (#)
            else if (c == '#') {
                token = line.substring(i);
                tokenLength = token.length();
                color = TextColor.ANSI.GREEN;
            }

            // 3. Triple-quoted docstring start
            else if (line.startsWith("\"\"\"", i) || line.startsWith("'''", i)) {
                String quote = line.substring(i, i + 3);
                int end = line.indexOf(quote, i + 3);

                if (end == -1) {
                    token = line.substring(i);
                    inDocstring = true;
                } else {
                    token = line.substring(i, end + 3);
                }
                tokenLength = token.length();
                color = TextColor.ANSI.YELLOW;
            }

            // 4. Standard single-line string literal
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
                while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '.')) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();
                color = TextColor.ANSI.MAGENTA;
            }

            // 6. Identifiers, functions, and keywords
            else if (Character.isLetter(c) || c == '_') {
                int end = i + 1;
                while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_')) {
                    end++;
                }
                token = line.substring(i, end);
                tokenLength = token.length();

                if (KEYWORDS.contains(token)) {
                    color = TextColor.ANSI.CYAN;
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

            // 7. Operators, delimiters, and spaces
            else {
                token = String.valueOf(c);
                tokenLength = 1;
                color = TextColor.ANSI.DEFAULT;
            }

            // Clip token against remaining render budget
            int remaining = maxWidth - drawn;
            String printableToken = (token.length() > remaining) ? token.substring(0, remaining) : token;

            tg.setForegroundColor(color);
            tg.putString(x + drawn, y, printableToken);

            drawn += printableToken.length();
            i += tokenLength;
        }

        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        return inDocstring;
    }
}