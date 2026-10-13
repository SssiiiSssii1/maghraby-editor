package org.lanterna;

import org.lanterna.ds.EditAction;
import org.lanterna.ds.MyStack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Text buffer + undo/redo history. The buffer is a list of lines; history stores
 * small deltas (EditAction) instead of whole-file snapshots.
 * All public methods are synchronized so the render thread and the input thread can share it.
 */
public class TextEditor {
    private List<StringBuilder> lines;
    private final MyStack undoStack;
    private final MyStack redoStack;
    private boolean dirty = false;

    public TextEditor() {
        this.lines = new ArrayList<>();
        this.lines.add(new StringBuilder());
        this.undoStack = new MyStack();
        this.redoStack = new MyStack();
    }

    // ------------------------------------------------------------------
    // Read access
    // ------------------------------------------------------------------
    public synchronized int getLineCount() {
        return lines.size();
    }

    public synchronized String getLine(int index) {
        if (index < 0 || index >= lines.size()) return "";
        return lines.get(index).toString();
    }

    public synchronized int getLineLength(int index) {
        if (index < 0 || index >= lines.size()) return 0;
        return lines.get(index).length();
    }

    /** Consistent snapshot of lines [from, to) taken under one lock (safe for the render thread). */
    public synchronized List<String> getLines(int from, int to) {
        from = Math.max(0, from);
        to = Math.min(to, lines.size());
        List<String> result = new ArrayList<>(Math.max(0, to - from));
        for (int i = from; i < to; i++) result.add(lines.get(i).toString());
        return result;
    }

    /** True when the buffer has changes that were not saved/loaded since. */
    public synchronized boolean isDirty() {
        return dirty;
    }

    // ------------------------------------------------------------------
    // Core buffer logic (no history bookkeeping here). Handles multi-line text.
    // ------------------------------------------------------------------
    private int[] insertData(int row, int col, String text) {
        if (row < 0 || row >= lines.size()) return new int[]{row, col};
        StringBuilder current = lines.get(row);
        col = Math.max(0, Math.min(col, current.length()));

        if (text.indexOf('\n') < 0) {
            current.insert(col, text);
            return new int[]{row, col + text.length()};
        }

        String[] parts = text.split("\n", -1);
        String tail = current.substring(col);
        current.setLength(col);
        current.append(parts[0]);
        int r = row;
        for (int k = 1; k < parts.length; k++) {
            r++;
            lines.add(r, new StringBuilder(parts[k]));
        }
        int endCol = parts[parts.length - 1].length();
        lines.get(r).append(tail);
        return new int[]{r, endCol};
    }

    private void removeData(int row, int col, String text) {
        if (row < 0 || row >= lines.size()) return;
        StringBuilder first = lines.get(row);
        col = Math.max(0, Math.min(col, first.length()));

        int newlines = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') newlines++;

        if (newlines == 0) {
            first.delete(col, Math.min(first.length(), col + text.length()));
            return;
        }

        int endRow = row + newlines;
        if (endRow >= lines.size()) return; // history no longer matches the buffer; do nothing
        StringBuilder last = lines.get(endRow);
        int endCol = Math.min(text.length() - text.lastIndexOf('\n') - 1, last.length());
        String tail = last.substring(endCol);
        first.setLength(col);
        first.append(tail);
        for (int r = endRow; r > row; r--) lines.remove(r);
    }

    // ------------------------------------------------------------------
    // User actions (all return the new cursor position as {row, col})
    // ------------------------------------------------------------------
    public synchronized int[] insertAt(int row, int col, String text) {
        if (text == null || text.isEmpty()) return new int[]{row, col};
        row = Math.max(0, Math.min(row, lines.size() - 1));
        col = Math.max(0, Math.min(col, lines.get(row).length()));

        undoStack.push(new EditAction(EditAction.Type.INSERT, row, col, text));
        redoStack.clear();
        dirty = true;
        return insertData(row, col, text);
    }

    /** Backspace: removes the character before (row, col), joining lines at column 0. */
    public synchronized int[] deleteAt(int row, int col) {
        if (row < 0 || row >= lines.size()) return new int[]{row, col};
        StringBuilder line = lines.get(row);
        col = Math.max(0, Math.min(col, line.length()));

        if (col > 0) {
            int start = col - 1;
            // never split a surrogate pair (emoji etc.)
            if (start > 0 && Character.isLowSurrogate(line.charAt(start))
                    && Character.isHighSurrogate(line.charAt(start - 1))) {
                start--;
            }
            String removed = line.substring(start, col);
            undoStack.push(new EditAction(EditAction.Type.DELETE, row, start, removed));
            redoStack.clear();
            dirty = true;
            removeData(row, start, removed);
            return new int[]{row, start};
        }
        if (row == 0) return new int[]{0, 0};

        int prevRow = row - 1;
        int prevCol = lines.get(prevRow).length();
        undoStack.push(new EditAction(EditAction.Type.DELETE, prevRow, prevCol, "\n"));
        redoStack.clear();
        dirty = true;
        removeData(prevRow, prevCol, "\n");
        return new int[]{prevRow, prevCol};
    }

    /** Delete key: removes the character under the cursor (or joins with the next line at end of line). */
    public synchronized int[] deleteForward(int row, int col) {
        if (row < 0 || row >= lines.size()) return new int[]{row, col};
        StringBuilder line = lines.get(row);
        col = Math.max(0, Math.min(col, line.length()));

        String removed;
        if (col < line.length()) {
            int end = col + 1;
            if (end < line.length() && Character.isHighSurrogate(line.charAt(col))
                    && Character.isLowSurrogate(line.charAt(end))) {
                end++;
            }
            removed = line.substring(col, end);
        } else if (row + 1 < lines.size()) {
            removed = "\n";
        } else {
            return new int[]{row, col};
        }

        undoStack.push(new EditAction(EditAction.Type.DELETE, row, col, removed, true));
        redoStack.clear();
        dirty = true;
        removeData(row, col, removed);
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
            removeData(action.row, action.col, action.text);
            cursor[0] = action.row;
            cursor[1] = action.col;
        } else {
            int[] pos = insertData(action.row, action.col, action.text);
            if (action.forward) {
                cursor[0] = action.row;
                cursor[1] = action.col;
            } else {
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
            int[] pos = insertData(action.row, action.col, action.text);
            cursor[0] = pos[0];
            cursor[1] = pos[1];
        } else {
            removeData(action.row, action.col, action.text);
            cursor[0] = action.row;
            cursor[1] = action.col;
        }
        return "Redone.";
    }

    // ------------------------------------------------------------------
    // File IO (always UTF-8)
    // ------------------------------------------------------------------

    /** Writes every line followed by '\n' (an empty buffer produces an empty file). */
    public synchronized String saveToFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) return "Error: Filename cannot be empty!";
        String name = filename.trim();
        try {
            Path path = Paths.get(name);
            boolean emptyBuffer = lines.size() == 1 && lines.get(0).length() == 0;
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                if (!emptyBuffer) {
                    for (StringBuilder line : lines) {
                        writer.write(line.toString());
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

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<StringBuilder> newLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (newLines.isEmpty() && line.startsWith("﻿")) line = line.substring(1); // strip BOM
                newLines.add(new StringBuilder(line));
            }
            if (newLines.isEmpty()) newLines.add(new StringBuilder());

            this.lines = newLines;
            undoStack.clear();
            redoStack.clear();
            dirty = false;
            return "Loaded '" + name + "' successfully.";
        } catch (CharacterCodingException e) {
            // Refuse instead of silently corrupting the file on the next save.
            return "Error: '" + name + "' is not valid UTF-8, refusing to open it.";
        } catch (IOException e) {
            return "Error loading file: " + e.getMessage();
        }
    }
}
