package org.maghraby.ds;

/**
 * Represents a single microscopic edit (Delta) in the editor.
 * Drastically reduces memory usage by saving only the change, not the whole file.
 */
public class EditAction {
    public enum Type { INSERT, DELETE }

    public final Type type;
    public final int row;
    public final int col;
    public final String text;
    /**
     * Only meaningful for DELETE: true when the text was removed with the Delete key
     * (cursor stays put), false for Backspace (cursor was after the removed text).
     * Lets undo put the cursor back exactly where it was.
     */
    public final boolean forward;

    public EditAction(Type type, int row, int col, String text) {
        this(type, row, col, text, false);
    }

    public EditAction(Type type, int row, int col, String text, boolean forward) {
        this.type = type;
        this.row = row;
        this.col = col;
        this.text = text;
        this.forward = forward;
    }
}
