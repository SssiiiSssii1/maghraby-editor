package org.lanterna; /**
 * Core text editor engine.
 * Manages the current text buffer, modifications (insert, delete, replace, find),
 * and state history for Undo/Redo operations using custom stacks.
 */

import org.lanterna.ds.MyStack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TextEditor {
    private StringBuilder currentText;
    private MyStack undoStack;
    private MyStack redoStack;

    public TextEditor() {
        this.currentText = new StringBuilder();
        this.undoStack = new MyStack();
        this.redoStack = new MyStack();
    }

    public String getCurrentText() {
        return currentText.toString();
    }

    /**
     * Inserts text at a specific buffer index and pushes the previous state to Undo stack.
     */
    public void insertAt(int index, String newText) {
        undoStack.push(currentText.toString());
        index = Math.max(0, Math.min(index, currentText.length()));
        currentText.insert(index, newText);
        redoStack.clear(); // Any new modification invalidates the redo history
    }

    /**
     * Deletes the character preceding the cursor index (Backspace behavior).
     */
    public void deleteAt(int index) {
        if (currentText.length() == 0 || index < 0 || index >= currentText.length()) return;

        undoStack.push(currentText.toString());
        currentText.deleteCharAt(index);
        redoStack.clear();
    }

    /**
     * Appends text to the end of the buffer (Command-line write operation).
     */
    public void write(String newText) {
        undoStack.push(currentText.toString());
        currentText.append(newText);
        redoStack.clear();
    }

    /**
     * Reverts to the previous state from the undo stack and saves the current state to redo.
     */
    public String undo() {
        if (undoStack.isEmpty()) return "Nothing to undo!";

        redoStack.push(currentText.toString());
        currentText.setLength(0);
        currentText.append(undoStack.pop());
        return "Undone.";
    }

    /**
     * Restores a reverted state from the redo stack and saves the current state to undo.
     */
    public String redo() {
        if (redoStack.isEmpty()) return "Nothing to redo!";

        undoStack.push(currentText.toString());
        currentText.setLength(0);
        currentText.append(redoStack.pop());
        return "Redone.";
    }

    /**
     * Finds the first occurrence of a target word.
     */
    public String find(String word) {
        int index = currentText.indexOf(word);
        if (index != -1) return word + " found at index: " + index;
        return word + " NOT found!";
    }

    /**
     * Replaces the first occurrence of a target word with a replacement string.
     */
    public String replace(String target, String replacement) {
        int index = currentText.indexOf(target);
        if (index == -1) return target + " NOT found!";

        undoStack.push(currentText.toString());
        currentText.replace(index, index + target.length(), replacement);
        redoStack.clear();
        return "Replaced '" + target + "' with '" + replacement + "'.";
    }

    /**
     * Deletes a given number of characters from the end of the text.
     */
    public String delete(int count) {
        if (currentText.length() == 0 || count <= 0) return "Nothing to delete!";

        undoStack.push(currentText.toString());
        int start = Math.max(0, currentText.length() - count);
        currentText.delete(start, currentText.length());
        redoStack.clear();
        return "Deleted last " + count + " characters.";
    }

    /**
     * Finds and deletes the first occurrence of a target word.
     */
    public String delete(String word) {
        int index = currentText.indexOf(word);
        if (index != -1) {
            undoStack.push(currentText.toString());
            currentText.delete(index, index + word.length());
            redoStack.clear();
            return "Deleted '" + word + "'.";
        }
        return "The word does NOT exist!";
    }

    /**
     * Saves the current buffer text into a file.
     * @param filename Target file path/name.
     * @return Status message for the UI.
     */
    public String saveToFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "Error: Filename cannot be empty!";
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename.trim()))) {
            writer.write(currentText.toString());
            return "Saved to '" + filename + "' successfully.";
        } catch (IOException e) {
            return "Error saving file: " + e.getMessage();
        }
    }

    /**
     * Loads text from a file into the editor buffer and preserves undo history.
     * @param filename Source file path/name.
     * @return Status message for the UI.
     */
    public String loadFromFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "Error: Filename cannot be empty!";
        }

        File file = new File(filename.trim());
        if (!file.exists()) {
            return "File '" + filename + "' not found. New file will be created on save.";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (!firstLine) {
                    sb.append("\n");
                }
                sb.append(line);
                firstLine = false;
            }

            undoStack.push(currentText.toString());
            currentText = sb;
            redoStack.clear();
            return "Loaded '" + filename + "' successfully.";
        } catch (IOException e) {
            return "Error loading file: " + e.getMessage();
        }
    }
}