package org.lanterna.syntax;

/**
 * Strategy interface defining how a single line is tokenized and styled with ANSI codes.
 */
public interface SyntaxHighlighter {

    /**
     * كلاس مساعد لإرجاع النص الملون مع حالة المحلل (State) معاً
     */
    class HighlightResult {
        public final String coloredLine;
        public final HighlightState nextState;

        public HighlightResult(String coloredLine, HighlightState nextState) {
            this.coloredLine = coloredLine;
            this.nextState = nextState;
        }
    }

    /**
     * يقوم بتحليل السطر وإضافة رموز ANSI للألوان.
     *
     * @param line  النص الأصلي للسطر.
     * @param state حالة المحلل في بداية هذا السطر (مثلاً: هل نحن داخل تعليق متعدد السطور؟).
     * @return نتيجة تحتوي على النص الملون برموز ANSI وحالة المحلل الجديدة.
     */
    HighlightResult highlightLine(String line, HighlightState state);

    /**
     * Checks if this highlighter supports the specified file extension.
     */
    boolean supportsExtension(String extension);
}