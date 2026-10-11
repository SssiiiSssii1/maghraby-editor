package org.lanterna;

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
import org.lanterna.syntax.HighlighterRegistry;
import org.lanterna.syntax.SyntaxHighlighter;

import java.io.IOException;

public class Main {
    private static final TextEditor editor = new TextEditor();
    private static final HighlighterRegistry highlighterRegistry = new HighlighterRegistry();

    // Tracks the first line index displayed at the top of the terminal viewport (vertical scrolling offset)
    private static int topLine = 0;

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
     * Calculates the current line index (0-based) in the text buffer based on cursorIndex.
     */
    private static int getCursorLineIndex() {
        String text = getText();
        int index = Math.max(0, Math.min(cursorIndex, text.length()));
        int line = 0;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * Converts the 1D buffer cursor index into 2D terminal screen coordinates,
     * taking the current vertical scroll offset (topLine) into account.
     */
    private static TerminalPosition getCursorPosition() {
        String text = getText();
        int index = Math.max(0, Math.min(cursorIndex, text.length()));

        int line = 0;
        int col = 1;

        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }

        // Screen row = first text row (2) + (actual buffer line - scroll offset topLine)
        int screenRow = 2 + (line - topLine);
        return new TerminalPosition(col, screenRow);
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

        // 1. Render the top title bar
        tg.setBackgroundColor(TextColor.ANSI.BLUE);
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        String headerTitle = " maghraby Editor | " + (currentFilename != null ? currentFilename : "[No Name]") + " -- Mode: " + currentMode;
        tg.putString(0, 0, padRight(headerTitle, cols));

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);

        // 2. Calculate text viewport boundaries
        String[] lines = getText().split("\n", -1);
        int firstTextRow = 2;
        int lastTextRowExclusive = Math.max(firstTextRow, rows - 2);
        int viewHeight = lastTextRowExclusive - firstTextRow; // Available vertical visible lines
        int availableWidth = Math.max(0, cols - 1);

        // 3. Adjust vertical scroll offset (topLine) to ensure cursor stays visible
        int cursorLine = getCursorLineIndex();
        if (cursorLine < topLine) {
            topLine = cursorLine;
        } else if (cursorLine >= topLine + viewHeight) {
            topLine = cursorLine - viewHeight + 1;
        }

        // 4. Resolve the syntax highlighter for the active file
        SyntaxHighlighter highlighter = highlighterRegistry.forFileName(currentFilename);
        boolean inBlockComment = false;

        // 5. Render visible lines starting from topLine offset
        for (int i = 0; i < viewHeight; i++) {
            int lineIndex = topLine + i;
            if (lineIndex >= lines.length) {
                break;
            }

            int screenRow = firstTextRow + i;
            String line = lines[lineIndex];

            if (cols > 1) {
                inBlockComment = highlighter.drawLine(tg, 1, screenRow, line, availableWidth, inBlockComment);
            }
        }

        // 6. Render the bottom status line
        if (rows >= 2) {
            tg.setBackgroundColor(TextColor.ANSI.WHITE);
            tg.setForegroundColor(TextColor.ANSI.BLACK);
            tg.putString(0, rows - 2, padRight(" " + statusMessage, cols));
        }

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);

        // 7. Update terminal cursor position
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

    private static void compileAndRun() {
        if (currentFilename == null || currentFilename.isEmpty()) {
            statusMessage = "Error: Save the file first with :w <filename>";
            return;
        }

        editor.saveToFile(currentFilename);

        try {
            screen.stopScreen();

            new ProcessBuilder("sh", "-c", "stty sane < /dev/tty 2>/dev/null || stty sane 2>/dev/null").inheritIO().start().waitFor();

            System.out.println("========================================");
            System.out.println(" Compiling & Running: " + currentFilename);
            System.out.println("========================================\n");

            ProcessBuilder pb;
            if (currentFilename.endsWith(".cpp")) {
                pb = new ProcessBuilder("sh", "-c", "stty sane && g++ \"" + currentFilename + "\" -o a.out && ./a.out");
            } else if (currentFilename.endsWith(".c")) {
                pb = new ProcessBuilder("sh", "-c", "stty sane && gcc \"" + currentFilename + "\" -o a.out && ./a.out");
            } else if (currentFilename.endsWith(".java")) {
                String className = currentFilename.replace(".java", "");
                pb = new ProcessBuilder("sh", "-c", "stty sane && javac \"" + currentFilename + "\" && java \"" + className + "\"");
            } else if (currentFilename.endsWith(".py")) {
                pb = new ProcessBuilder("sh", "-c", "stty sane && python3 \"" + currentFilename + "\"");
            } else {
                pb = new ProcessBuilder("sh", "-c", "stty sane && bash \"" + currentFilename + "\"");
            }

            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();

            System.out.println("\n----------------------------------------");
            System.out.println("Press [Enter] to return to the editor...");
            System.in.read();

            new ProcessBuilder("sh", "-c", "stty -icanon -echo min 1 < /dev/tty 2>/dev/null").inheritIO().start().waitFor();

            screen.startScreen();
            statusMessage = "Execution finished.";

        } catch (Exception e) {
            try {
                new ProcessBuilder("sh", "-c", "stty -icanon -echo min 1 < /dev/tty 2>/dev/null").inheritIO().start().waitFor();
                screen.startScreen();
            } catch (IOException | InterruptedException ignored) {}
            statusMessage = "Error running compiler: " + e.getMessage();
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
            if (!args.isEmpty()) {
                currentFilename = args;
                statusMessage = editor.saveToFile(currentFilename);
            } else if (currentFilename != null && !currentFilename.isEmpty()) {
                statusMessage = editor.saveToFile(currentFilename);
            } else {
                statusMessage = "Usage: :w <filename.txt>";
            }
        } else if (cmd.equals("load") || cmd.equals("e") || cmd.equals("open")) {
            if (!args.isEmpty()) {
                currentFilename = args;
                statusMessage = editor.loadFromFile(currentFilename);
                cursorIndex = 0;
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
        } else if (cmd.equals("run") || cmd.equals("compile") || cmd.equals("c")) {
            compileAndRun();
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
