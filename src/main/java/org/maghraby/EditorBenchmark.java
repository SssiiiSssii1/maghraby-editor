package org.maghraby;

/**
 * Benchmark utility to test the performance of the TextEditor.
 * Measures the time taken to build a large file and the cost of
 * inserting/deleting at the very beginning of the file (worst-case scenario).
 */
public class EditorBenchmark {
    public static void main(String[] args) {
        System.out.println("=== TextEditor Benchmark Test ===");
        TextEditor editor = new TextEditor();

        // ------------------------------------------------------------------
        // Phase 1: Simulate opening a large file
        // ------------------------------------------------------------------
        System.out.println("Building a mock file with 100,000 lines...");
        long startTime = System.currentTimeMillis();

        StringBuilder initialContent = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            initialContent.append("This is line number ").append(i).append(" for performance testing.\n");
        }

        // Load content directly to simulate file opening
        editor.loadInitialContent(initialContent.toString());

        long endTime = System.currentTimeMillis();
        System.out.println("Time to build file: " + (endTime - startTime) + " ms");
        System.out.println("Total lines: " + editor.getLineCount());
        System.out.println("-------------------------------------------------");

        // ------------------------------------------------------------------
        // Phase 2: Worst-Case Insertions (Top of the file)
        // ------------------------------------------------------------------
        System.out.println("Inserting 100 new lines at the top (line by line)...");
        startTime = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            editor.insertAt(0, 0, "New Line inserted at the top\n");
        }

        endTime = System.currentTimeMillis();
        System.out.println("Time for top insertions: " + (endTime - startTime) + " ms");
        System.out.println("-------------------------------------------------");

        // ------------------------------------------------------------------
        // Phase 3: Worst-Case Deletions (Top of the file)
        // ------------------------------------------------------------------
        System.out.println("Deleting 100 characters from the top...");
        startTime = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            editor.deleteForward(0, 0);
        }

        endTime = System.currentTimeMillis();
        System.out.println("Time for top deletions: " + (endTime - startTime) + " ms");
        System.out.println("=================================================");
        System.out.println("Piece Table should yield near 0ms for inserts and deletes!");
    }
}
