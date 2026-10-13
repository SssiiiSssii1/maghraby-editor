package org.lanterna;

import org.lanterna.syntax.HighlightState;
import org.lanterna.syntax.HighlighterRegistry;
import org.lanterna.syntax.SyntaxHighlighter;
import org.lanterna.syntax.SyntaxHighlighter.HighlightResult;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Terminal text editor (vim-like modes) with syntax highlighting and :run / :compile.
 * Needs a real terminal that supports ANSI escapes and `stty` (Linux, macOS, WSL, Git-Bash...).
 */
public class Main {
    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------
    private static volatile boolean running = true;
    private static volatile boolean needsRedraw = true;
    private static final AtomicBoolean forceClear = new AtomicBoolean(true);

    private static final TextEditor editor = new TextEditor();
    private static final HighlighterRegistry highlighterRegistry = new HighlighterRegistry();
    private static volatile String currentFilename = "Untitled.txt";

    // 2D cursor (character coordinates inside the buffer)
    private static volatile int cursorRow = 0;
    private static volatile int cursorCol = 0;
    private static int goalCol = 0;          // remembered column for Up/Down (input thread only)
    private static boolean keepGoal = false; // set by vertical moves (input thread only)

    // Viewport (render thread only)
    private static int topLine = 0;
    private static int leftCol = 0;

    private static final String DEFAULT_STATUS =
            "NORMAL | i:Insert  ::Command  u:Undo  r:Redo  F5:Run  q:Quit  (:help)";
    private static final String HELP =
            ":w [file]  :wq  :q  :q!  :run [args]  :compile  |  F5 run  Ctrl+S save  Ctrl+Q force-quit";

    private static volatile String statusMessage = DEFAULT_STATUS;
    private static volatile Mode currentMode = Mode.NORMAL;
    private static final StringBuffer commandInput = new StringBuffer();

    private static volatile int termRows = 24;
    private static volatile int termCols = 80;

    private static final int TAB_WIDTH = 4;

    // Rendering is paused while an external program owns the terminal
    private static final Object RENDER_LOCK = new Object();
    private static volatile boolean renderPaused = false;

    private static String originalStty = null;
    private static final AtomicBoolean terminalRestored = new AtomicBoolean(false);

    private static final PrintStream out = createOut();

    // ANSI
    private static final String ESC = "\033";
    private static final String ANSI_HIDE_CURSOR = ESC + "[?25l";
    private static final String ANSI_SHOW_CURSOR = ESC + "[?25h";
    private static final String ANSI_RESET = ESC + "[0m";
    private static final String ANSI_CLEAR_SCREEN = ESC + "[2J";
    private static final String ANSI_CLEAR_LINE = ESC + "[2K";
    private static final String ANSI_ENTER_ALT_SCREEN = ESC + "[?1049h";
    private static final String ANSI_LEAVE_ALT_SCREEN = ESC + "[?1049l";
    private static final String ANSI_BG_BLUE_FG_WHITE = ESC + "[44m" + ESC + "[37m";
    private static final String ANSI_BG_WHITE_FG_BLACK = ESC + "[47m" + ESC + "[30m";

    enum Mode { NORMAL, INSERT, COMMAND }

    private static PrintStream createOut() {
        try {
            return new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16),
                    false, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return System.out;
        }
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------
    public static void main(String[] args) {
        originalStty = stty("-g");
        if (originalStty == null) {
            System.err.println("Error: this editor needs a real terminal (could not configure the TTY with 'stty').");
            System.err.println("Run it from a terminal on Linux / macOS / WSL / Git-Bash, not from an IDE run console.");
            System.exit(1);
            return;
        }

        if (args.length > 0 && !args[0].trim().isEmpty()) {
            currentFilename = args[0].trim();
            statusMessage = editor.loadFromFile(currentFilename);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(Main::restoreTerminal, "Terminal-Restore"));

        Thread renderThread = null;
        try {
            updateTerminalSize();
            enterEditorScreen();

            renderThread = new Thread(Main::renderLoop, "Render-Thread");
            renderThread.start();

            Thread resizeThread = new Thread(Main::resizeWatcher, "Resize-Watcher");
            resizeThread.setDaemon(true);
            resizeThread.start();

            inputLoop();
        } catch (Throwable t) {
            running = false;
            restoreTerminal();
            t.printStackTrace();
        } finally {
            running = false;
            if (renderThread != null) {
                try { renderThread.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            restoreTerminal();
        }
    }

    // ------------------------------------------------------------------
    // Input thread
    // ------------------------------------------------------------------
    private static void inputLoop() throws IOException {
        InputStream in = System.in;
        boolean lastWasCR = false;

        while (running) {
            int b = in.read();                   // blocking: no busy polling, and EOF ends the editor
            if (b == -1) break;

            boolean isCR = false;
            keepGoal = false;
            try {
                if (b == 17) {                                   // Ctrl+Q: force quit
                    running = false;
                    break;
                } else if (b == 19) {                            // Ctrl+S
                    saveCommand("");
                } else if (b == 27) {
                    handleEscapeSequence(in);
                } else if (b == 127 || b == 8) {
                    handleBackspace();
                } else if (b == 13) {
                    isCR = true;
                    handleEnter();
                } else if (b == 10) {
                    if (!lastWasCR) handleEnter();               // swallow the LF of a pasted CR LF pair
                } else if (b == 9) {
                    handleTab();
                } else if (b >= 32 && b < 127) {
                    handleText(String.valueOf((char) b));
                } else if (b >= 0xC2 && b <= 0xF4) {
                    String s = readUtf8(in, b);
                    if (s != null) handleText(s);
                }
            } catch (RuntimeException e) {
                statusMessage = "Internal error: " + e;           // never lose the session over one bad key
            }
            lastWasCR = isCR;

            clampCursor();
            if (!keepGoal) goalCol = cursorCol;
            needsRedraw = true;
        }
    }

    /** Reads one byte, waiting at most {@code ms}. Returns -1 on timeout. */
    private static int readTimeout(InputStream in, int ms) throws IOException {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (in.available() <= 0) {
            if (System.nanoTime() >= end) return -1;
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        return in.read();
    }

    private static String readUtf8(InputStream in, int lead) throws IOException {
        int extra = lead >= 0xF0 ? 3 : lead >= 0xE0 ? 2 : 1;
        byte[] buf = new byte[extra + 1];
        buf[0] = (byte) lead;
        for (int k = 1; k <= extra; k++) {
            int c = readTimeout(in, 50);
            if (c == -1 || (c & 0xC0) != 0x80) return null;
            buf[k] = (byte) c;
        }
        String s = new String(buf, StandardCharsets.UTF_8);
        return s.indexOf('�') >= 0 ? null : s;
    }

    /** Called right after an ESC byte: distinguishes a bare Esc key from arrow/Home/End/Delete/F-key sequences. */
    private static void handleEscapeSequence(InputStream in) throws IOException {
        int next = readTimeout(in, 30);
        if (next == -1) {                       // plain Esc key
            handleEscape();
            return;
        }
        if (next == 'O') {                      // SS3: ESC O A ...  (application cursor keys, F1-F4)
            int c = readTimeout(in, 30);
            if (c == 'A') handleArrow(-1, 0);
            else if (c == 'B') handleArrow(1, 0);
            else if (c == 'C') handleArrow(0, 1);
            else if (c == 'D') handleArrow(0, -1);
            else if (c == 'H') handleHome();
            else if (c == 'F') handleEnd();
            return;
        }
        if (next != '[') {                      // Alt+key etc.: treat as Esc
            handleEscape();
            return;
        }

        // CSI: ESC [ <params> <final byte 0x40-0x7E>
        StringBuilder params = new StringBuilder();
        int fin = -1;
        for (int n = 0; n < 16; n++) {
            int c = readTimeout(in, 30);
            if (c == -1) return;
            if (c >= 0x40 && c <= 0x7E) { fin = c; break; }
            params.append((char) c);
        }
        if (fin == -1) return;

        String p = params.toString();
        int semi = p.indexOf(';');
        String base = semi >= 0 ? p.substring(0, semi) : p;   // ignore modifiers (Ctrl/Shift+arrow)

        switch (fin) {
            case 'A': handleArrow(-1, 0); break;
            case 'B': handleArrow(1, 0); break;
            case 'C': handleArrow(0, 1); break;
            case 'D': handleArrow(0, -1); break;
            case 'H': handleHome(); break;
            case 'F': handleEnd(); break;
            case '~':
                switch (base) {
                    case "1": case "7": handleHome(); break;
                    case "4": case "8": handleEnd(); break;
                    case "3": handleDeleteKey(); break;
                    case "5": handlePage(-1); break;
                    case "6": handlePage(1); break;
                    case "15": runCommand(true, ""); break;       // F5
                    default: break;
                }
                break;
            default: break;
        }
    }

    // ------------------------------------------------------------------
    // Key handlers (run on the input thread)
    // ------------------------------------------------------------------
    private static void handleText(String s) {
        if (currentMode == Mode.INSERT) {
            int[] pos = editor.insertAt(cursorRow, cursorCol, s);
            cursorRow = pos[0];
            cursorCol = pos[1];
        } else if (currentMode == Mode.COMMAND) {
            commandInput.append(s);
        } else if (s.length() == 1) {
            char c = s.charAt(0);
            if (c == 'q') {
                tryQuit();
            } else if (c == 'i') {
                currentMode = Mode.INSERT;
                statusMessage = "-- INSERT MODE -- (Esc: back to Normal)";
            } else if (c == ':') {
                currentMode = Mode.COMMAND;
                commandInput.setLength(0);
                statusMessage = "Command mode: " + HELP;
            } else if (c == 'u') {
                int[] pos = {cursorRow, cursorCol};
                statusMessage = editor.undo(pos);
                cursorRow = pos[0];
                cursorCol = pos[1];
            } else if (c == 'r') {
                int[] pos = {cursorRow, cursorCol};
                statusMessage = editor.redo(pos);
                cursorRow = pos[0];
                cursorCol = pos[1];
            }
        }
    }

    private static void handleTab() {
        if (currentMode != Mode.INSERT) return;
        int spaces = TAB_WIDTH - (cursorCol % TAB_WIDTH);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spaces; i++) sb.append(' ');
        handleText(sb.toString());
    }

    private static void handleBackspace() {
        if (currentMode == Mode.INSERT) {
            int[] pos = editor.deleteAt(cursorRow, cursorCol);
            cursorRow = pos[0];
            cursorCol = pos[1];
        } else if (currentMode == Mode.COMMAND) {
            if (commandInput.length() > 0) {
                commandInput.deleteCharAt(commandInput.length() - 1);
            } else {
                currentMode = Mode.NORMAL;          // backspace on an empty ':' line cancels it (like vim)
                statusMessage = "NORMAL MODE";
            }
        }
    }

    private static void handleDeleteKey() {
        if (currentMode != Mode.INSERT) return;
        int[] pos = editor.deleteForward(cursorRow, cursorCol);
        cursorRow = pos[0];
        cursorCol = pos[1];
    }

    private static void handleEnter() {
        if (currentMode == Mode.INSERT) {
            int[] pos = editor.insertAt(cursorRow, cursorCol, "\n");
            cursorRow = pos[0];
            cursorCol = pos[1];
        } else if (currentMode == Mode.COMMAND) {
            String cmd = commandInput.toString().trim();
            commandInput.setLength(0);
            currentMode = Mode.NORMAL;
            executeCommand(cmd);
        }
    }

    private static void handleEscape() {
        if (currentMode != Mode.NORMAL) {
            currentMode = Mode.NORMAL;
            commandInput.setLength(0);
            statusMessage = "NORMAL MODE";
        }
    }

    private static void handleArrow(int rowDelta, int colDelta) {
        if (currentMode == Mode.COMMAND) return;

        if (colDelta < 0) {
            if (cursorCol > 0) {
                cursorCol--;
            } else if (cursorRow > 0) {
                cursorRow--;
                cursorCol = editor.getLineLength(cursorRow);
            }
        } else if (colDelta > 0) {
            if (cursorCol < editor.getLineLength(cursorRow)) {
                cursorCol++;
            } else if (cursorRow < editor.getLineCount() - 1) {
                cursorRow++;
                cursorCol = 0;
            }
        }

        if (rowDelta != 0) {
            cursorRow += rowDelta;
            cursorCol = goalCol;                    // clampCursor() trims it to the new line's length
            keepGoal = true;
        }
    }

    private static void handleHome() {
        if (currentMode != Mode.COMMAND) cursorCol = 0;
    }

    private static void handleEnd() {
        if (currentMode != Mode.COMMAND) cursorCol = editor.getLineLength(cursorRow);
    }

    private static void handlePage(int direction) {
        if (currentMode == Mode.COMMAND) return;
        int step = Math.max(1, termRows - 4);
        cursorRow += direction * step;
        cursorCol = goalCol;
        keepGoal = true;
    }

    private static void clampCursor() {
        int maxRow = Math.max(0, editor.getLineCount() - 1);
        cursorRow = Math.max(0, Math.min(cursorRow, maxRow));
        cursorCol = Math.max(0, Math.min(cursorCol, editor.getLineLength(cursorRow)));
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------
    private static void tryQuit() {
        if (editor.isDirty()) {
            statusMessage = "Unsaved changes! :w to save, :wq to save+quit, :q! (or Ctrl+Q) to discard.";
        } else {
            running = false;
        }
    }

    private static boolean saveCommand(String arg) {
        String target = arg.isEmpty() ? currentFilename : arg;
        String msg = editor.saveToFile(target);
        statusMessage = msg;
        boolean ok = msg.startsWith("Saved");
        if (ok) currentFilename = target;
        return ok;
    }

    private static void executeCommand(String cmdLine) {
        if (cmdLine.isEmpty()) {
            statusMessage = "NORMAL MODE";
            return;
        }
        String[] parts = cmdLine.split("\\s+", 2);
        String name = parts[0];
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (name) {
            case "q": case "quit": case "exit":
                tryQuit();
                break;
            case "q!": case "quit!":
                running = false;
                break;
            case "w": case "write":
                saveCommand(arg);
                break;
            case "wq": case "x":
                if (saveCommand(arg)) running = false;
                break;
            case "run":
                runCommand(true, arg);
                break;
            case "compile": case "build":
                runCommand(false, arg);
                break;
            case "help": case "h":
                statusMessage = HELP;
                break;
            default:
                statusMessage = "Unknown command: " + cmdLine + "   (try :help)";
        }
    }

    /** Saves, builds (and optionally runs) the current file with the terminal handed over to the program. */
    private static void runCommand(boolean run, String argLine) {
        String file = currentFilename;
        if (CodeRunner.detect(file) == null) {
            statusMessage = "Can't " + (run ? "run" : "compile") + " '" + file + "'. Supported: "
                    + CodeRunner.supportedExtensions() + "  (rename with :w name.ext)";
            return;
        }
        String saved = editor.saveToFile(file);
        if (!saved.startsWith("Saved")) {
            statusMessage = saved;
            return;
        }

        CodeRunner.Plan plan;
        try {
            plan = CodeRunner.plan(file, run, run ? splitArgs(argLine) : new ArrayList<String>());
        } catch (CodeRunner.RunnerException e) {
            statusMessage = e.getMessage();
            return;
        }

        String summary;
        suspendTerminal();
        try {
            summary = CodeRunner.execute(plan, out);
            out.print("\n" + ESC + "[7m Press Enter to return to the editor " + ANSI_RESET);
            out.flush();
            waitForEnter();
        } finally {
            CodeRunner.cleanup(plan);
            resumeTerminal();
        }
        statusMessage = summary;
    }

    private static void waitForEnter() {
        try {
            int ch;
            while ((ch = System.in.read()) != -1 && ch != '\n') { /* swallow */ }
            if (ch == -1) running = false;
        } catch (IOException ignored) {
            running = false;
        }
    }

    /** Minimal argument splitter: whitespace separated, double or single quotes group words. */
    private static List<String> splitArgs(String line) {
        List<String> args = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean has = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0; else cur.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                has = true;
            } else if (Character.isWhitespace(c)) {
                if (has || cur.length() > 0) { args.add(cur.toString()); cur.setLength(0); has = false; }
            } else {
                cur.append(c);
            }
        }
        if (has || cur.length() > 0) args.add(cur.toString());
        return args;
    }

    // ------------------------------------------------------------------
    // Render thread
    // ------------------------------------------------------------------
    private static void renderLoop() {
        while (running) {
            try {
                if (needsRedraw && !renderPaused) {
                    needsRedraw = false;            // clear BEFORE reading state: a key pressed mid-render re-arms it
                    String frame = buildFrame();
                    synchronized (RENDER_LOCK) {
                        if (!renderPaused) {
                            out.print(frame);
                            out.flush();
                        } else {
                            needsRedraw = true;
                        }
                    }
                }
            } catch (Throwable t) {
                statusMessage = "Render error: " + t;
            }
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String buildFrame() {
        int rows = termRows;
        int cols = termCols;
        StringBuilder sb = new StringBuilder(rows * (cols + 16));
        sb.append(ANSI_HIDE_CURSOR);
        if (forceClear.getAndSet(false)) sb.append(ANSI_CLEAR_SCREEN);

        if (rows < 5 || cols < 20) {
            sb.append(ESC).append("[1;1H").append(ANSI_CLEAR_LINE).append("Terminal too small");
            return sb.toString();
        }

        // ---- snapshot of shared state ----
        Mode mode = currentMode;
        String status = statusMessage;
        String command = commandInput.toString();
        String filename = currentFilename;
        int row = cursorRow;
        int col = cursorCol;
        int textRows = rows - 3;

        int lineCount = editor.getLineCount();
        row = Math.max(0, Math.min(row, lineCount - 1));

        // ---- viewport (vertical) ----
        if (row < topLine) topLine = row;
        else if (row >= topLine + textRows) topLine = row - textRows + 1;
        topLine = Math.max(0, Math.min(topLine, Math.max(0, lineCount - 1)));

        // One locked copy of everything from line 0 to the last visible line
        List<String> all = editor.getLines(0, topLine + textRows);
        String cursorLineText = row < all.size() ? all.get(row) : editor.getLine(row);
        col = Math.max(0, Math.min(col, cursorLineText.length()));

        // ---- viewport (horizontal), measured in screen columns (tabs expanded) ----
        int vcol = visualCol(cursorLineText, col);
        if (vcol < leftCol) leftCol = vcol;
        else if (vcol >= leftCol + cols) leftCol = vcol - cols + 1;
        leftCol = Math.max(0, leftCol);

        // ---- header ----
        String header = " maghraby Editor | " + sanitize(filename) + (editor.isDirty() ? " [+]" : "")
                + " | Mode: " + mode;
        sb.append(ESC).append("[1;1H").append(ANSI_BG_BLUE_FG_WHITE).append(padRight(header, cols)).append(ANSI_RESET);

        // ---- text area ----
        SyntaxHighlighter highlighter = highlighterRegistry.forFileName(filename);

        // Carry multi-line state (block comments, docstrings) from the top of the file down to the first visible line
        HighlightState state = HighlightState.NORMAL;
        for (int i = 0; i < topLine && i < all.size(); i++) {
            state = highlighter.highlightLine(all.get(i), state).nextState;
        }

        for (int i = 0; i < textRows; i++) {
            int idx = topLine + i;
            sb.append(ESC).append('[').append(i + 2).append(";1H").append(ANSI_CLEAR_LINE);
            if (idx < all.size()) {
                String display = expandTabsAndSanitize(all.get(idx));
                HighlightResult hr = highlighter.highlightLine(display, state);   // highlight the WHOLE line...
                state = hr.nextState;
                sb.append(sliceAnsi(hr.coloredLine, leftCol, cols));              // ...then cut the visible window
            }
        }

        // ---- status + command line ----
        sb.append(ESC).append('[').append(rows - 1).append(";1H")
          .append(ANSI_BG_WHITE_FG_BLACK).append(padRight(" " + sanitize(status), cols)).append(ANSI_RESET);

        sb.append(ESC).append('[').append(rows).append(";1H").append(ANSI_CLEAR_LINE);
        if (mode == Mode.COMMAND) {
            String shown = ":" + sanitize(command);
            if (shown.length() > cols - 1) shown = shown.substring(shown.length() - (cols - 1));
            sb.append(shown);
            sb.append(ESC).append('[').append(rows).append(';').append(Math.min(shown.length() + 1, cols)).append('H');
        } else {
            int screenRow = (row - topLine) + 2;
            int screenCol = (vcol - leftCol) + 1;
            sb.append(ESC).append('[').append(Math.max(2, Math.min(screenRow, rows - 2))).append(';')
              .append(Math.max(1, Math.min(screenCol, cols))).append('H');
        }
        sb.append(ANSI_SHOW_CURSOR);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------
    private static boolean isControl(char c) {
        return c < 0x20 || (c >= 0x7F && c < 0xA0);
    }

    /** Replaces control characters so file contents can never inject terminal escape sequences. */
    private static String sanitize(String s) {
        boolean clean = true;
        for (int i = 0; i < s.length(); i++) if (isControl(s.charAt(i))) { clean = false; break; }
        if (clean) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(isControl(c) ? '·' : c);
        }
        return sb.toString();
    }

    /** Tabs become spaces up to the next tab stop; other control characters become a visible dot. */
    private static String expandTabsAndSanitize(String line) {
        StringBuilder sb = new StringBuilder(line.length() + 8);
        int v = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\t') {
                int n = TAB_WIDTH - (v % TAB_WIDTH);
                for (int k = 0; k < n; k++) sb.append(' ');
                v += n;
            } else {
                sb.append(isControl(c) ? '·' : c);
                v++;
            }
        }
        return sb.toString();
    }

    /** Screen column of character index {@code col} in {@code line} once tabs are expanded. */
    private static int visualCol(String line, int col) {
        int v = 0;
        int end = Math.min(col, line.length());
        for (int i = 0; i < end; i++) {
            v += line.charAt(i) == '\t' ? TAB_WIDTH - (v % TAB_WIDTH) : 1;
        }
        return v;
    }

    /**
     * Returns the part of an ANSI-coloured string that falls in screen columns [start, start+width).
     * Escape sequences are kept (so colours stay correct after horizontal scrolling) but never counted as width.
     */
    static String sliceAnsi(String s, int start, int width) {
        StringBuilder sb = new StringBuilder(s.length());
        int end = start + width;
        int col = 0;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == 0x1b && i + 1 < n && s.charAt(i + 1) == '[') {
                int j = i + 2;
                while (j < n && !(s.charAt(j) >= 0x40 && s.charAt(j) <= 0x7E)) j++;
                if (j < n) j++;
                if (col < end) sb.append(s, i, j);
                i = j;
                continue;
            }
            if (col >= end) break;
            int cp = s.codePointAt(i);
            int len = Character.charCount(cp);   // a surrogate pair counts as 2 columns (matches the cursor maths)
            if (col >= start && col + len <= end) sb.append(s, i, i + len);
            col += len;
            i += len;
        }
        sb.append(ANSI_RESET);
        return sb.toString();
    }

    private static String padRight(String text, int width) {
        if (width <= 0) return "";
        if (text.length() >= width) return text.substring(0, width);
        StringBuilder sb = new StringBuilder(width);
        sb.append(text);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Terminal handling
    // ------------------------------------------------------------------
    /** Runs `stty <args>` against the controlling terminal. Returns trimmed stdout, or null on failure. */
    private static String stty(String args) {
        try {
            Process p = new ProcessBuilder("sh", "-c", "stty " + args + " < /dev/tty")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            p.getOutputStream().close();
            String result = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            return code == 0 ? result.trim() : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static void updateTerminalSize() {
        String s = stty("size");
        if (s == null) return;
        String[] p = s.split("\\s+");
        if (p.length != 2) return;
        try {
            int r = Integer.parseInt(p[0]);
            int c = Integer.parseInt(p[1]);
            if (r > 0 && c > 0 && (r != termRows || c != termCols)) {
                termRows = r;
                termCols = c;
                forceClear.set(true);
                needsRedraw = true;
            }
        } catch (NumberFormatException ignored) { }
    }

    /** Re-reads the terminal size regularly so resizing the window re-lays out the editor. */
    private static void resizeWatcher() {
        while (running) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }
            if (!renderPaused) updateTerminalSize();
        }
    }

    private static void enterEditorScreen() {
        stty("raw -echo");
        out.print(ANSI_ENTER_ALT_SCREEN + ANSI_CLEAR_SCREEN + ANSI_HIDE_CURSOR);
        out.flush();
    }

    /** Gives the terminal back to the user's program: normal screen, normal tty modes, visible cursor. */
    private static void suspendTerminal() {
        synchronized (RENDER_LOCK) {
            renderPaused = true;                // any frame in progress has finished once we hold the lock
        }
        out.print(ANSI_RESET + ANSI_SHOW_CURSOR + ANSI_LEAVE_ALT_SCREEN);
        out.flush();
        stty(originalStty);
    }

    private static void resumeTerminal() {
        stty("raw -echo");
        out.print(ANSI_ENTER_ALT_SCREEN + ANSI_CLEAR_SCREEN + ANSI_HIDE_CURSOR);
        out.flush();
        updateTerminalSize();
        forceClear.set(true);
        needsRedraw = true;
        renderPaused = false;
    }

    /** Idempotent; also used as the shutdown hook so the shell is never left in raw mode. */
    private static void restoreTerminal() {
        if (!terminalRestored.compareAndSet(false, true)) return;
        out.print(ANSI_RESET + ANSI_SHOW_CURSOR + ANSI_LEAVE_ALT_SCREEN);
        out.flush();
        if (originalStty != null) {
            if (stty(originalStty) == null) stty("sane");
        }
    }
}
