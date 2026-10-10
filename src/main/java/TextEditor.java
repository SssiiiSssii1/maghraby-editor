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

    public void write(String newText) {
        undoStack.push(String.valueOf(currentText));

        currentText.append(newText);
        redoStack.clear();
    }

    public String undo() {
        if (undoStack.isEmpty()) {
            return "Nothing to undo!";
        }
        redoStack.push(String.valueOf(currentText));
        currentText.setLength(0);
        currentText.append(undoStack.pop());
        return "Undone.";
    }

    public String redo() {
        if (redoStack.isEmpty()) {
            return "Nothing to redo!";
        }
        undoStack.push(String.valueOf(currentText));
        currentText.setLength(0);
        currentText.append(redoStack.pop());
        return "Redone.";
    }

    public void insertAt(int index, String newText) {
        undoStack.push(currentText.toString());
        index = Math.max(0, Math.min(index, currentText.length()));
        currentText.insert(index, newText);
        redoStack.clear();
    }

    public void deleteAt(int index) {
        if (currentText.length() == 0 || index <= 0) {
            return;
        }
        undoStack.push(currentText.toString());
        index = Math.min(index, currentText.length());
        currentText.deleteCharAt(index - 1);
        redoStack.clear();
    }

    public String find(String word) {
        int index = currentText.indexOf(word);
        if (index != -1) {
            return word + " found at index: " + index;
        } else {
            return word + " NOT found!";
        }
    }

    public String replace(String target, String replacement) {
        int index = currentText.indexOf(target);
        if (index == -1) {
            return target + " NOT found!";
        }
        undoStack.push(currentText.toString());
        currentText.replace(index, index + target.length(), replacement);
        redoStack.clear();
        return "Replaced '" + target + "' with '" + replacement + "'.";
    }

    public String delete(int count) {
        if (currentText.isEmpty() || count <= 0) {
            return "Nothing to delete!";
        }
        undoStack.push(currentText.toString());
        int start = Math.max(0, currentText.length() - count);
        int end = currentText.length();
        currentText.delete(start, end);
        redoStack.clear();
        return "Deleted last " + count + " characters.";
    }

    public String delete(String word) {
        int index = currentText.indexOf(word);
        if (index != -1) {
            undoStack.push(currentText.toString());
            int start = index;
            int end = index + word.length();
            currentText.delete(start, end);
            redoStack.clear();
            return "Deleted '" + word + "'.";
        } else {
            return "The word does NOT exist!";
        }
    }
}