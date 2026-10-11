package org.lanterna.syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Registry storing all registered highlighters and resolving the correct
 * implementation based on a file's extension.
 */
public class HighlighterRegistry {

    private final List<SyntaxHighlighter> highlighters = new ArrayList<>();
    private final SyntaxHighlighter fallback = new PlainTextHighlighter();

    public HighlighterRegistry() {
        registerDefaultHighlighters();
    }

    private void registerDefaultHighlighters() {
        // Java
        register(new CLikeHighlighter(
                Set.of("java"),
                Set.of(
                        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
                        "char", "class", "const", "continue", "default", "do", "double",
                        "else", "enum", "extends", "final", "finally", "float", "for",
                        "if", "implements", "import", "instanceof", "int", "interface",
                        "long", "native", "new", "package", "private", "protected",
                        "public", "return", "short", "static", "strictfp", "super",
                        "switch", "synchronized", "this", "throw", "throws", "transient",
                        "try", "void", "volatile", "while", "var", "record", "yield",
                        "sealed", "permits"
                )
        ));

        // C / C++
        register(new CLikeHighlighter(
                Set.of("c", "cpp", "cc", "cxx", "h", "hpp"),
                Set.of(
                        "auto", "bool", "break", "case", "catch", "char", "class", "const",
                        "constexpr", "continue", "default", "delete", "do", "double",
                        "else", "enum", "explicit", "export", "extern", "false", "float",
                        "for", "friend", "goto", "if", "inline", "int", "long", "mutable",
                        "namespace", "new", "noexcept", "nullptr", "operator", "override",
                        "private", "protected", "public", "register", "reinterpret_cast",
                        "return", "short", "signed", "sizeof", "static", "static_assert",
                        "struct", "switch", "template", "this", "thread_local", "throw",
                        "true", "try", "typedef", "typeid", "typename", "union", "unsigned",
                        "using", "virtual", "void", "volatile", "while"
                )
        ));

        // Python
        register(new PythonHighlighter());
    }

    public void register(SyntaxHighlighter highlighter) {
        highlighters.add(highlighter);
    }

    /**
     * Resolves the highlighter for the provided filename by inspecting its extension.
     */
    public SyntaxHighlighter forFileName(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return fallback;
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);

        for (SyntaxHighlighter highlighter : highlighters) {
            if (highlighter.supportsExtension(extension)) {
                return highlighter;
            }
        }

        return fallback;
    }
}