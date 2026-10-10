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
    private static TextEditor editor = new TextEditor();
    private static Screen screen;
    private static String statusMessage = "NORMAL MODE | Press 'i' to Insert, ':' for Command, 'u' for Undo, Ctrl+R for Redo, 'q' to Quit";
    private static Mode currentMode = Mode.NORMAL;
    private static StringBuilder commandInput = new StringBuilder();
    private static int cursorCol = 1;
    private static int cursorRow = 2;

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

            while (true) {
                render(screen);
                KeyStroke key = screen.readInput();

                if (key == null) continue;

                if (currentMode == Mode.NORMAL) {
                    if (handleNormalMode(key)) break;
                } else if (currentMode == Mode.INSERT) {
                    handleInsertMode(key);
                } else if (currentMode == Mode.COMMAND) {
                    handleCommandMode(key);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void render(Screen screen) throws IOException {
        screen.clear();
        TerminalSize size = screen.getTerminalSize();
        int rows = size.getRows();
        int cols = size.getColumns();

        TextGraphics tg = screen.newTextGraphics();

        screen.setCursorPosition(new TerminalPosition(cursorCol, cursorRow));

        tg.setBackgroundColor(TextColor.ANSI.BLUE);
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        String header = " Console Text Editor -- Mode: " + currentMode;
        tg.putString(0, 0, padRight(header, cols));

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        String text = editor.getCurrentText();

        int currentRow = 2;
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (currentRow >= rows - 2) break;
            tg.putString(1, currentRow++, line);
        }

        tg.setBackgroundColor(TextColor.ANSI.WHITE);
        tg.setForegroundColor(TextColor.ANSI.BLACK);
        tg.putString(0, rows - 2, padRight(" " + statusMessage, cols));

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        if (currentMode == Mode.COMMAND) {
            tg.putString(0, rows - 1, ":" + commandInput.toString());
            screen.setCursorPosition(new TerminalPosition(1 + commandInput.length(), rows - 1));
        } else {
            screen.setCursorPosition(new TerminalPosition(cursorCol, cursorRow));
        }

        screen.refresh();
    }

    private static boolean handleNormalMode(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            if (c == 'q') {
                return true;
            } else if (c == 'i') {
                currentMode = Mode.INSERT;
                statusMessage = "-- INSERT MODE -- (Press ESC to return to Normal Mode)";
            } else if (c == ':') {
                currentMode = Mode.COMMAND;
                commandInput.setLength(0);
                statusMessage = "Enter command (w <text>, d <word|count>, f <word>, r <old> <new>)";
            } else if (c == 'u') {
                statusMessage = editor.undo();
            }
        } else if (key.getKeyType() == KeyType.ReverseTab || (key.isCtrlDown() && key.getCharacter() == 'r')) {
            statusMessage = editor.redo();
        }

        if (key.getKeyType() == KeyType.ArrowLeft || key.getKeyType() == KeyType.ArrowRight ||
                key.getKeyType() == KeyType.ArrowUp   || key.getKeyType() == KeyType.ArrowDown) {
            handleArrowKeys(key);
            return false;
        }

        return false;
    }

    private static void handleInsertMode(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            currentMode = Mode.NORMAL;
            statusMessage = "NORMAL MODE";
            return;
        }

        if (key.getKeyType() == KeyType.ArrowLeft || key.getKeyType() == KeyType.ArrowRight ||
                key.getKeyType() == KeyType.ArrowUp   || key.getKeyType() == KeyType.ArrowDown) {
            handleArrowKeys(key);
            return;
        }

        TerminalSize size = screen.getTerminalSize();
        int maxCols = size.getColumns() - 2;
        int maxRows = size.getRows() - 3;

        if (key.getKeyType() == KeyType.Character) {
            int idx = getCursorBufferIndex();
            editor.insertAt(idx, String.valueOf(key.getCharacter()));

            if (cursorCol >= maxCols) {
                if (cursorRow < maxRows) {
                    cursorRow++;
                    cursorCol = 1;
                }
            } else {
                cursorCol++;
            }
        } else if (key.getKeyType() == KeyType.Backspace) {
            int idx = getCursorBufferIndex();
            if (idx > 0) {
                editor.deleteAt(idx);
                if (cursorCol <= 1) {
                    if (cursorRow > 2) {
                        cursorRow--;
                        cursorCol = maxCols;
                    }
                } else {
                    cursorCol--;
                }
            }
        } else if (key.getKeyType() == KeyType.Enter) {
            int idx = getCursorBufferIndex();
            editor.insertAt(idx, "\n");
            cursorRow++;
            cursorCol = 1;
        }
    }

    private static void handleArrowKeys(KeyStroke key) {
        TerminalSize size = screen.getTerminalSize();
        int maxCols = size.getColumns() - 2;
        int maxRows = size.getRows() - 3;

        if (key.getKeyType() == KeyType.ArrowRight) {
            if (cursorCol >= maxCols) {
                if (cursorRow < maxRows) {
                    cursorRow++;
                    cursorCol = 1;
                }
            } else {
                cursorCol++;
            }
        } else if (key.getKeyType() == KeyType.ArrowLeft) {
            if (cursorCol <= 1) {
                if (cursorRow > 2) {
                    cursorRow--;
                    cursorCol = maxCols;
                }
            } else {
                cursorCol--;
            }
        } else if (key.getKeyType() == KeyType.ArrowUp) {
            if (cursorRow > 2) cursorRow--;
        } else if (key.getKeyType() == KeyType.ArrowDown) {
            if (cursorRow < maxRows) cursorRow++;
        }
    }

    private static int getCursorBufferIndex() {
        String text = editor.getCurrentText();
        String[] lines = text.split("\n", -1);

        int targetLineIdx = cursorRow - 2;
        int index = 0;

        for (int i = 0; i < lines.length && i < targetLineIdx; i++) {
            index += lines[i].length() + 1;
        }

        if (targetLineIdx < lines.length) {
            int colOffset = Math.max(0, cursorCol - 1);
            index += Math.min(colOffset, lines[targetLineIdx].length());
        } else {
            index = text.length();
        }

        return Math.min(index, text.length());
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
            return;
        }

        if (key.getKeyType() == KeyType.Backspace) {
            if (commandInput.length() > 0) {
                commandInput.deleteCharAt(commandInput.length() - 1);
            }
        } else if (key.getKeyType() == KeyType.Character) {
            commandInput.append(key.getCharacter());
        }
    }

    private static void handleArrowKeys(KeyStroke key, Screen screen) {
        TerminalSize size = screen.getTerminalSize();
        int maxRows = size.getRows() - 3;
        int maxCols = size.getColumns() - 2;

        if (key.getKeyType() == KeyType.ArrowLeft) {
            if (cursorCol > 1) cursorCol--;
        } else if (key.getKeyType() == KeyType.ArrowRight) {
            if (cursorCol < maxCols) cursorCol++;
        } else if (key.getKeyType() == KeyType.ArrowUp) {
            if (cursorRow > 2) cursorRow--;
        } else if (key.getKeyType() == KeyType.ArrowDown) {
            if (cursorRow < maxRows) cursorRow++;
        }
    }

    private static void executeCommand(String input) {
        if (input.isEmpty()) return;

        String[] tokens = input.split("\\s+", 2);
        String cmd = tokens[0].toLowerCase();
        String args = tokens.length > 1 ? tokens[1].trim() : "";

        if (cmd.equals("q") || cmd.equals("exit")) {
            System.exit(0);
        } else if (cmd.equals("w") || cmd.equals("write")) {
            if (!args.isEmpty()) {
                editor.write(args);
                statusMessage = "Added: " + args;
            }
        } else if (cmd.equals("d") || cmd.equals("delete")) {
            if (!args.isEmpty()) {
                try {
                    int count = Integer.parseInt(args);
                    statusMessage = editor.delete(count);
                } catch (NumberFormatException e) {
                    statusMessage = editor.delete(args);
                }
            }
        } else if (cmd.equals("f") || cmd.equals("find")) {
            if (!args.isEmpty()) {
                statusMessage = editor.find(args);
            }
        } else if (cmd.equals("r") || cmd.equals("replace")) {
            String[] parts = args.split("\\s+", 2);
            if (parts.length == 2) {
                statusMessage = editor.replace(parts[0], parts[1]);
            } else {
                statusMessage = "Usage: r <old> <new>";
            }
        } else {
            statusMessage = "Unknown command: " + cmd;
        }
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}