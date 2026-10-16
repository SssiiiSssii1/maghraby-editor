package org.maghraby;

import org.maghraby.ds.EditAction;
import org.maghraby.ds.MyStack;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Text buffer + undo/redo history utilizing a Piece Table data structure.
 * The Piece Table guarantees O(1) insertions and deletions by keeping the original
 * file content read-only and appending new keystrokes to an addBuffer.
 */
public class TextEditor {
    private final MyStack undoStack;
    private final MyStack redoStack;
    private boolean dirty = false;

    // --- Piece Table Core Components ---
    String originalBuffer;      // Read-only buffer loaded from the file
    StringBuilder addBuffer;    // Append-only buffer for new user inputs
    List<Piece> pieces;         // The sequence of pieces linking buffers together

    public TextEditor() {
        this.undoStack = new MyStack();
        this.redoStack = new MyStack();
        loadInitialContent(""); // Initialize an empty Piece Table
    }

    // ------------------------------------------------------------------
    // Piece Table Logic & Helpers
    // ------------------------------------------------------------------

    public void loadInitialContent(String fileContent) {
        this.originalBuffer = fileContent != null ? fileContent : "";
        this.addBuffer = new StringBuilder();
        this.pieces = new ArrayList<>();

        Piece initialPiece = new Piece();
        initialPiece.source = BufferType.ORIGINAL;
        initialPiece.start = 0;
        initialPiece.length = this.originalBuffer.length();

        this.pieces.add(initialPiece);
    }

    public String getFullText() {
        if (pieces == null || pieces.isEmpty()) return "";
        StringBuilder fullText = new StringBuilder();

        for (Piece p : pieces) {
            if (p.source == BufferType.ORIGINAL) {
                fullText.append(originalBuffer, p.start, p.start + p.length);
            } else {
                fullText.append(addBuffer.substring(p.start, p.start + p.length));
            }
        }
        return fullText.toString();
    }

    private int getOffset(int row, int col) {
        String[] allLines = getAllLines();
        if (allLines.length == 0) return 0;

        row = Math.max(0, Math.min(row, allLines.length - 1));
        int offset = 0;

        for (int i = 0; i < row; i++) {
            offset += allLines[i].length() + 1; // +1 to account for the '\n'
        }

        return offset + Math.max(0, Math.min(col, allLines[row].length()));
    }

    private int[] calculateNewPosition(int row, int col, String text) {
        int newlines = 0;
        int lastNewlineIdx = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                newlines++;
                lastNewlineIdx = i;
            }
        }
        if (newlines == 0) {
            return new int[]{row, col + text.length()};
        } else {
            return new int[]{row + newlines, text.length() - lastNewlineIdx - 1};
        }
    }

    // ------------------------------------------------------------------
    // Read Access
    // ------------------------------------------------------------------

    private String[] getAllLines() {
        String text = getFullText();
        return text.isEmpty() ? new String[]{""} : text.split("\n", -1);
    }

    public synchronized int getLineCount() {
        return getAllLines().length;
    }

    public synchronized String getLine(int index) {
        String[] allLines = getAllLines();
        if (index < 0 || index >= allLines.length) return "";
        return allLines[index];
    }

    public synchronized int getLineLength(int index) {
        String[] allLines = getAllLines();
        if (index < 0 || index >= allLines.length) return 0;
        return allLines[index].length();
    }

    public synchronized List<String> getLines(int from, int to) {
        String[] allLines = getAllLines();
        from = Math.max(0, from);
        to = Math.min(to, allLines.length);
        List<String> result = new ArrayList<>(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            result.add(allLines[i]);
        }
        return result;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    // ------------------------------------------------------------------
    // Core Modifiers
    // ------------------------------------------------------------------

    public void insertText(int offset, String newText) {
        if (newText == null || newText.isEmpty()) return;

        int addStart = addBuffer.length();
        addBuffer.append(newText);

        Piece newPiece = new Piece();
        newPiece.source = BufferType.ADD;
        newPiece.start = addStart;
        newPiece.length = newText.length();

        int currentOffset = 0;
        for (int i = 0; i < pieces.size(); i++) {
            Piece current = pieces.get(i);

            if (offset >= currentOffset && offset <= currentOffset + current.length) {
                int splitPoint = offset - currentOffset;

                Piece left = new Piece();
                left.source = current.source;
                left.start = current.start;
                left.length = splitPoint;

                Piece right = new Piece();
                right.source = current.source;
                right.start = current.start + splitPoint;
                right.length = current.length - splitPoint;

                pieces.remove(i);

                if (right.length > 0) pieces.add(i, right);
                pieces.add(i, newPiece);
                if (left.length > 0) pieces.add(i, left);

                break;
            }
            currentOffset += current.length;
        }
    }

    public void deleteText(int offset, int deleteLength) {
        if (offset < 0 || pieces == null) return;

        int currentOffset = 0;
        for (int i = 0; i < pieces.size(); i++) {
            Piece current = pieces.get(i);

            if (offset >= currentOffset && offset < currentOffset + current.length) {
                int splitPoint = offset - currentOffset;

                Piece left = new Piece();
                left.source = current.source;
                left.start = current.start;
                left.length = splitPoint;

                Piece right = new Piece();
                right.source = current.source;
                right.start = current.start + splitPoint + deleteLength;
                right.length = current.length - splitPoint - deleteLength;

                pieces.remove(i);

                if (right.length > 0) pieces.add(i, right);
                if (left.length > 0) pieces.add(i, left);

                break;
            }
            currentOffset += current.length;
        }
    }

    // ------------------------------------------------------------------
    // User Actions
    // ------------------------------------------------------------------

    public synchronized int[] insertAt(int row, int col, String text) {
        if (text == null || text.isEmpty()) return new int[]{row, col};

        String[] allLines = getAllLines();
        row = Math.max(0, Math.min(row, allLines.length - 1));
        col = Math.max(0, Math.min(col, allLines[row].length()));

        undoStack.push(new EditAction(EditAction.Type.INSERT, row, col, text));
        redoStack.clear();
        dirty = true;

        int offset = getOffset(row, col);
        insertText(offset, text);

        return calculateNewPosition(row, col, text);
    }

    public synchronized int[] deleteAt(int row, int col) {
        String[] allLines = getAllLines();
        if (row < 0 || row >= allLines.length) return new int[]{row, col};
        String line = allLines[row];
        col = Math.max(0, Math.min(col, line.length()));

        if (col > 0) {
            int start = col - 1;
            if (start > 0 && Character.isLowSurrogate(line.charAt(start))
                    && Character.isHighSurrogate(line.charAt(start - 1))) {
                start--;
            }
            String removed = line.substring(start, col);

            undoStack.push(new EditAction(EditAction.Type.DELETE, row, start, removed));
            redoStack.clear();
            dirty = true;

            int offset = getOffset(row, start);
            deleteText(offset, removed.length());

            return new int[]{row, start};
        }

        if (row == 0) return new int[]{0, 0};

        int prevRow = row - 1;
        int prevCol = allLines[prevRow].length();

        undoStack.push(new EditAction(EditAction.Type.DELETE, prevRow, prevCol, "\n"));
        redoStack.clear();
        dirty = true;

        int offset = getOffset(prevRow, prevCol);
        deleteText(offset, 1);

        return new int[]{prevRow, prevCol};
    }

    public synchronized int[] deleteForward(int row, int col) {
        String[] allLines = getAllLines();
        if (row < 0 || row >= allLines.length) return new int[]{row, col};
        String line = allLines[row];
        col = Math.max(0, Math.min(col, line.length()));

        String removed;
        if (col < line.length()) {
            int end = col + 1;
            if (end < line.length() && Character.isHighSurrogate(line.charAt(col))
                    && Character.isLowSurrogate(line.charAt(end))) {
                end++;
            }
            removed = line.substring(col, end);
        } else if (row + 1 < allLines.length) {
            removed = "\n";
        } else {
            return new int[]{row, col};
        }

        undoStack.push(new EditAction(EditAction.Type.DELETE, row, col, removed, true));
        redoStack.clear();
        dirty = true;

        int offset = getOffset(row, col);
        deleteText(offset, removed.length());

        return new int[]{row, col};
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    public synchronized String undo(int[] cursor) {
        if (undoStack.isEmpty()) return "Nothing to undo!";
        EditAction action = undoStack.pop();
        redoStack.push(action);
        dirty = true;

        if (action.type == EditAction.Type.INSERT) {
            int offset = getOffset(action.row, action.col);
            deleteText(offset, action.text.length());
            cursor[0] = action.row;
            cursor[1] = action.col;
        } else {
            int offset = getOffset(action.row, action.col);
            insertText(offset, action.text);
            if (action.forward) {
                cursor[0] = action.row;
                cursor[1] = action.col;
            } else {
                int[] pos = calculateNewPosition(action.row, action.col, action.text);
                cursor[0] = pos[0];
                cursor[1] = pos[1];
            }
        }
        return "Undone.";
    }

    public synchronized String redo(int[] cursor) {
        if (redoStack.isEmpty()) return "Nothing to redo!";
        EditAction action = redoStack.pop();
        undoStack.push(action);
        dirty = true;

        if (action.type == EditAction.Type.INSERT) {
            int offset = getOffset(action.row, action.col);
            insertText(offset, action.text);
            int[] pos = calculateNewPosition(action.row, action.col, action.text);
            cursor[0] = pos[0];
            cursor[1] = pos[1];
        } else {
            int offset = getOffset(action.row, action.col);
            deleteText(offset, action.text.length());
            cursor[0] = action.row;
            cursor[1] = action.col;
        }
        return "Redone.";
    }

    // ------------------------------------------------------------------
    // File IO
    // ------------------------------------------------------------------

    public synchronized String saveToFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) return "Error: Filename cannot be empty!";
        String name = filename.trim();
        try {
            Path path = Paths.get(name);
            String[] allLines = getAllLines();
            boolean emptyBuffer = allLines.length == 1 && allLines[0].isEmpty();

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                if (!emptyBuffer) {
                    for (String line : allLines) {
                        writer.write(line);
                        writer.write('\n');
                    }
                }
            }
            dirty = false;
            return "Saved to '" + name + "' successfully.";
        } catch (IOException | InvalidPathException e) {
            return "Error saving file: " + e.getMessage();
        }
    }

    public synchronized String loadFromFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) return "Error: Filename cannot be empty!";
        String name = filename.trim();
        Path path;
        try {
            path = Paths.get(name);
        } catch (InvalidPathException e) {
            return "Error loading file: " + e.getMessage();
        }
        if (!Files.exists(path)) return "File '" + name + "' not found. New file will be created on save.";
        if (Files.isDirectory(path)) return "Error: '" + name + "' is a directory.";

        try {
            byte[] bytes = Files.readAllBytes(path);
            String content = new String(bytes, StandardCharsets.UTF_8);

            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }

            loadInitialContent(content);
            undoStack.clear();
            redoStack.clear();
            dirty = false;
            return "Loaded '" + name + "' successfully.";
        } catch (Exception e) {
            return "Error loading file: " + e.getMessage();
        }
    }
}