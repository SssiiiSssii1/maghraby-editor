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
    private static String statusMessage = "NORMAL MODE | Press 'i' to Insert, ':' for Command, 'u' for Undo, Ctrl+R for Redo, 'q' to Quit";
    private static Mode currentMode = Mode.NORMAL;
    private static StringBuilder commandInput = new StringBuilder();

    enum Mode {
        NORMAL,
        INSERT,
        COMMAND
    }

    public static void main(String[] args) {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        Screen screen = null;

        try {
            Terminal terminal = factory.createTerminal();
            screen = new TerminalScreen(terminal);
            screen.startScreen();
            screen.setCursorPosition(null); // إخفاء أو ضبط الكرسور حسب الحاجة

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

    // رسم واجهة المحرر بالكامل
    private static void render(Screen screen) throws IOException {
        screen.clear();
        TerminalSize size = screen.getTerminalSize();
        int rows = size.getRows();
        int cols = size.getColumns();

        TextGraphics tg = screen.newTextGraphics();

        // 1. شريط العنوان العلوي
        tg.setBackgroundColor(TextColor.ANSI.BLUE);
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        String header = " Console Text Editor -- Mode: " + currentMode;
        tg.putString(0, 0, padRight(header, cols));

        // 2. عرض محتوى النص (Buffer)
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        String text = editor.getCurrentText();

        // تقسيم النص لأسطر وعرضه داخل الشاشة
        int currentRow = 2;
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (currentRow >= rows - 2) break;
            tg.putString(1, currentRow++, line);
        }

        // 3. شريط الحالة السفلي (Status Bar)
        tg.setBackgroundColor(TextColor.ANSI.WHITE);
        tg.setForegroundColor(TextColor.ANSI.BLACK);
        tg.putString(0, rows - 2, padRight(" " + statusMessage, cols));

        // 4. سطر الأوامر أو الإدخال (السطر الأخير)
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        if (currentMode == Mode.COMMAND) {
            tg.putString(0, rows - 1, ":" + commandInput.toString());
            screen.setCursorPosition(new TerminalPosition(1 + commandInput.length(), rows - 1));
        } else if (currentMode == Mode.INSERT) {
            // مؤشر الكتابة في نهاية النص
            int curCol = (lines.length > 0 ? lines[lines.length - 1].length() + 1 : 1);
            screen.setCursorPosition(new TerminalPosition(curCol, Math.max(2, currentRow - 1)));
        } else {
            screen.setCursorPosition(new TerminalPosition(0, rows - 1));
        }

        screen.refresh();
    }

    // معالجة الضغطات في وضع NORMAL
    private static boolean handleNormalMode(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            if (c == 'q') {
                return true; // خروج
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
        return false;
    }

    // معالجة الكتابة المباشرة في وضع INSERT
    private static void handleInsertMode(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            currentMode = Mode.NORMAL;
            statusMessage = "NORMAL MODE";
            return;
        }

        if (key.getKeyType() == KeyType.Character) {
            editor.write(String.valueOf(key.getCharacter()));
        } else if (key.getKeyType() == KeyType.Backspace) {
            editor.delete(1);
        } else if (key.getKeyType() == KeyType.Enter) {
            editor.write("\n");
        }
    }

    // معالجة كتابة الأوامر في وضع COMMAND
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

    // تنفيذ الأمر المكتوب
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