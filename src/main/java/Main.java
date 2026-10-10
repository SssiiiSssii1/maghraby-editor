import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;

public class Main {
    private static final TextEditor editor = new TextEditor();

    private static String currentFilename = null;
    private static Screen screen;
    private static String statusMessage =
            "NORMAL MODE | i: Insert | :: Command | u: Undo | Ctrl+R: Redo | q: Quit";
    private static Mode currentMode = Mode.NORMAL;
    private static final StringBuilder commandInput = new StringBuilder();

    // The cursor has one source of truth: its index inside the text buffer.
    private static int cursorIndex = 0;
    private static boolean running = true;

    enum Mode {
        NORMAL,
        INSERT,
        COMMAND
    }

    public static void main(String[] args) {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();

        try {
            Terminal terminal = factory.createTerminal();
            screen = new TerminalScreen(terminal);
            screen.startScreen();
            screen.setCursorPosition(null);

            if (args.length > 0 && !args[0].trim().isEmpty()) {
                currentFilename = args[0].trim();
                statusMessage = editor.loadFromFile(currentFilename);
            }

            while (running) {
                render(screen);
                KeyStroke key = screen.readInput();

                if (key == null) {
                    continue;
                }

                switch (currentMode) {
                    case NORMAL:
                        handleNormalMode(key);
                        break;
                    case INSERT:
                        handleInsertMode(key);
                        break;
                    case COMMAND:
                        handleCommandMode(key);
                        break;
                    default:
                        break;
                }

                clampCursorIndex();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (IOException ignored) {
                    // The terminal is already closing.
                }
            }
        }
    }

    private static String getText() {
        String text = editor.getCurrentText();
        return text == null ? "" : text;
    }

    private static void clampCursorIndex() {
        cursorIndex = Math.max(0, Math.min(cursorIndex, getText().length()));
    }

    /**
     * Convert the single text-buffer index into terminal coordinates.
     * Text begins at column 1, row 2 because the header occupies row 0.
     */
    private static TerminalPosition getCursorPosition() {
        String text = getText();
        int index = Math.max(0, Math.min(cursorIndex, text.length()));

        int row = 2;
        int col = 1;

        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                row++;
                col = 1;
            } else {
                col++;
            }
        }

        return new TerminalPosition(col, row);
    }

    private static void render(Screen screen) throws IOException {
        screen.clear();

        TerminalSize size = screen.getTerminalSize();
        int rows = size.getRows();
        int cols = size.getColumns();

        if (rows <= 0 || cols <= 0) {
            screen.refresh();
            return;
        }

        TextGraphics tg = screen.newTextGraphics();

        tg.setBackgroundColor(TextColor.ANSI.BLUE);
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(0, 0, padRight(" Console Text Editor -- Mode: " + currentMode, cols));

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);

        String[] lines = getText().split("\n", -1);
        int firstTextRow = 2;
        int lastTextRowExclusive = Math.max(firstTextRow, rows - 2);
        int availableWidth = Math.max(0, cols - 1);

        for (int i = 0; i < lines.length; i++) {
            int screenRow = firstTextRow + i;
            if (screenRow >= lastTextRowExclusive) {
                break;
            }

            String line = lines[i];
            String visibleLine = line.length() > availableWidth
                    ? line.substring(0, availableWidth)
                    : line;

            if (!visibleLine.isEmpty() && cols > 1) {
                tg.putString(1, screenRow, visibleLine);
            }
        }

        if (rows >= 2) {
            tg.setBackgroundColor(TextColor.ANSI.WHITE);
            tg.setForegroundColor(TextColor.ANSI.BLACK);
            tg.putString(0, rows - 2, padRight(" " + statusMessage, cols));
        }

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);

        if (currentMode == Mode.COMMAND && rows >= 1) {
            String commandLine = ":" + commandInput;
            tg.putString(0, rows - 1, clipToWidth(commandLine, cols));

            int commandCol = Math.min(commandLine.length(), Math.max(0, cols - 1));
            screen.setCursorPosition(new TerminalPosition(commandCol, rows - 1));
        } else {
            TerminalPosition position = getCursorPosition();

            int cursorCol = Math.min(position.getColumn(), Math.max(0, cols - 1));
            int cursorRow = Math.min(position.getRow(), Math.max(0, rows - 3));

            screen.setCursorPosition(new TerminalPosition(cursorCol, cursorRow));
        }

        screen.refresh();
    }

    private static void handleNormalMode(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();

            if (c == 'q') {
                running = false;
            } else if (c == 'i') {
                currentMode = Mode.INSERT;
                statusMessage = "-- INSERT MODE -- (Press ESC to return to Normal Mode)";
            } else if (c == ':') {
                currentMode = Mode.COMMAND;
                commandInput.setLength(0);
                statusMessage = "Commands: w <text>, d <count|word>, f <word>, r <old> <new>, q";
            } else if (c == 'u') {
                statusMessage = editor.undo();
                clampCursorIndex();
            }
        } else if (key.isCtrlDown()
                && key.getKeyType() == KeyType.Character
                && key.getCharacter() == 'r') {
            statusMessage = editor.redo();
            clampCursorIndex();
        } else if (key.getKeyType() == KeyType.ReverseTab) {
            statusMessage = editor.redo();
            clampCursorIndex();
        } else {
            handleArrowKeys(key);
        }
    }

    private static void handleInsertMode(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            currentMode = Mode.NORMAL;
            statusMessage = "NORMAL MODE";
            return;
        }

        if (isArrowKey(key.getKeyType())) {
            handleArrowKeys(key);
            return;
        }

        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();

            // Ignore control characters; Enter is handled separately below.
            if (!Character.isISOControl(c)) {
                editor.insertAt(cursorIndex, String.valueOf(c));
                cursorIndex++;
            }
        } else if (key.getKeyType() == KeyType.Enter) {
            editor.insertAt(cursorIndex, "\n");
            cursorIndex++;
        } else if (key.getKeyType() == KeyType.Backspace) {
            if (cursorIndex > 0) {
                editor.deleteAt(cursorIndex - 1);
                cursorIndex--;
            }
        } else if (key.getKeyType() == KeyType.Delete) {
            if (cursorIndex < getText().length()) {
                editor.deleteAt(cursorIndex);
            }
        }
    }

    private static boolean isArrowKey(KeyType type) {
        return type == KeyType.ArrowLeft
                || type == KeyType.ArrowRight
                || type == KeyType.ArrowUp
                || type == KeyType.ArrowDown;
    }

    private static void handleArrowKeys(KeyStroke key) {
        String text = getText();

        switch (key.getKeyType()) {
            case ArrowLeft:
                if (cursorIndex > 0) {
                    cursorIndex--;
                }
                break;

            case ArrowRight:
                if (cursorIndex < text.length()) {
                    cursorIndex++;
                }
                break;

            case ArrowUp:
                moveCursorVertical(text, -1);
                break;

            case ArrowDown:
                moveCursorVertical(text, 1);
                break;

            default:
                break;
        }
    }

    /**
     * Move one logical line up or down, keeping the cursor's column when possible.
     */
    private static void moveCursorVertical(String text, int direction) {
        int currentLineStart = text.lastIndexOf('\n', Math.max(-1, cursorIndex - 1)) + 1;
        int desiredColumn = cursorIndex - currentLineStart;

        if (direction < 0) {
            if (currentLineStart == 0) {
                return;
            }

            int previousLineEnd = currentLineStart - 1;
            int previousLineStart = text.lastIndexOf('\n',
                    Math.max(-1, previousLineEnd - 1)) + 1;
            int previousLineLength = previousLineEnd - previousLineStart;

            cursorIndex = previousLineStart + Math.min(desiredColumn, previousLineLength);
        } else {
            int currentLineEnd = text.indexOf('\n', cursorIndex);
            if (currentLineEnd == -1) {
                return;
            }

            int nextLineStart = currentLineEnd + 1;
            int nextLineEnd = text.indexOf('\n', nextLineStart);
            if (nextLineEnd == -1) {
                nextLineEnd = text.length();
            }

            int nextLineLength = nextLineEnd - nextLineStart;
            cursorIndex = nextLineStart + Math.min(desiredColumn, nextLineLength);
        }
    }

    private static void handleCommandMode(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            currentMode = Mode.NORMAL;
            statusMessage = "Command cancelled.";
            return;
        }

        if (key.getKeyType() == KeyType.Enter) {
            executeCommand(commandInput.toString().trim());
            currentMode = Mode.NORMAL;
            clampCursorIndex();
            return;
        }

        if (key.getKeyType() == KeyType.Backspace) {
            if (commandInput.length() > 0) {
                commandInput.deleteCharAt(commandInput.length() - 1);
            }
        } else if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            if (!Character.isISOControl(c)) {
                commandInput.append(c);
            }
        }
    }

    private static void executeCommand(String input) {
        if (input.isEmpty()) {
            return;
        }

        String[] tokens = input.split("\\s+", 2);
        String cmd = tokens[0].toLowerCase();
        String args = tokens.length > 1 ? tokens[1].trim() : "";

        if (cmd.equals("q") || cmd.equals("exit")) {
            running = false;
        } else if (cmd.equals("w") || cmd.equals("write") || cmd.equals("save")) {
            // 1. حفظ الملف (يدعم :w filename.txt أو :w السريع)
            if (!args.isEmpty()) {
                currentFilename = args;
                statusMessage = editor.saveToFile(currentFilename);
            } else if (currentFilename != null && !currentFilename.isEmpty()) {
                statusMessage = editor.saveToFile(currentFilename);
            } else {
                statusMessage = "Usage: :w <filename.txt>";
            }
        } else if (cmd.equals("load") || cmd.equals("e") || cmd.equals("open")) {
            // 2. فتح ملف موجود
            if (!args.isEmpty()) {
                currentFilename = args;
                statusMessage = editor.loadFromFile(currentFilename);
                cursorIndex = 0; // إعادة المؤشر للبداية بعد التحميل
            } else {
                statusMessage = "Usage: :e <filename.txt> or :load <filename.txt>";
            }
        } else if (cmd.equals("d") || cmd.equals("delete")) {
            if (args.isEmpty()) {
                statusMessage = "Usage: d <count|word>";
            } else {
                try {
                    int count = Integer.parseInt(args);
                    statusMessage = editor.delete(count);
                } catch (NumberFormatException e) {
                    statusMessage = editor.delete(args);
                }
                clampCursorIndex();
            }
        } else if (cmd.equals("f") || cmd.equals("find")) {
            if (!args.isEmpty()) {
                statusMessage = editor.find(args);
            } else {
                statusMessage = "Usage: f <word>";
            }
        } else if (cmd.equals("r") || cmd.equals("replace")) {
            String[] parts = args.split("\\s+", 2);
            if (parts.length == 2) {
                statusMessage = editor.replace(parts[0], parts[1]);
                clampCursorIndex();
            } else {
                statusMessage = "Usage: r <old> <new>";
            }
        } else {
            statusMessage = "Unknown command: " + cmd;
        }
    }

    private static String clipToWidth(String text, int width) {
        if (width <= 0) {
            return "";
        }
        return text.length() <= width ? text : text.substring(0, width);
    }

    private static String padRight(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (text.length() >= width) {
            return text.substring(0, width);
        }

        StringBuilder result = new StringBuilder(text);
        while (result.length() < width) {
            result.append(' ');
        }
        return result.toString();
    }
}