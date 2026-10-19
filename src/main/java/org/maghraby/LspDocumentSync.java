package org.maghraby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.maghraby.Main.showSuggestionsUi;

/**
 * Keeps one open document in sync with a language server without ever blocking the editor.
 *
 * The editor thread only appends a small change record to a list (see {@link EditListener}). A single
 * background thread debounces those records and sends them as ONE textDocument/didChange.
 *
 * Lock order is always  editor monitor -> {@code lock}.  The background thread never takes the editor
 * monitor while holding {@code lock}, so the two threads cannot deadlock.
 */
public final class LspDocumentSync implements EditListener, AutoCloseable {

    private static final long DEBOUNCE_MS = 150;
    private static final String[] ROOT_MARKERS = {
            ".git", "go.mod", "pom.xml", "build.gradle", "build.gradle.kts", "compile_commands.json",
            "pyproject.toml", "setup.py"
    };

    private final LspClient client;
    private final TextEditor editor;
    private final String languageId;
    private final ScheduledExecutorService exec;

    private final Object lock = new Object();
    // --- guarded by lock ---
    private boolean opened = false;
    private String uri = null;
    private int version = 0;
    private List<Object> pending = new ArrayList<>(); // incremental changes, in edit order
    private boolean changed = false;                    // something changed since the last flush
    private ScheduledFuture<?> flushTask = null;

    public LspDocumentSync(LspClient client, TextEditor editor, String languageId) {
        this.client = client;
        this.editor = editor;
        this.languageId = languageId;
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LSP-Sync");
            t.setDaemon(true);
            return t;
        });
        editor.setChangeListener(this);
    }

    /**
     * Finds a server for the file and starts it. Returns null when there is no server for this language
     * or it could not be launched. The server is started in the background; diagnostics arrive through
     * the listener whenever it is ready. Call {@link #open(Path)} afterwards.
     */
    public static LspDocumentSync tryStart(String filename, TextEditor editor, LspClient.Listener listener) {
        Path file;
        try {
            file = Paths.get(filename.trim()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return null;
        }
        Path root = findRoot(file);
        LspServers.Spec spec = LspServers.forFile(file.getFileName().toString(), root);
        if (spec == null) return null;
        try {
            LspClient client = LspClient.start(spec.command, root, listener);
            return new LspDocumentSync(client, editor, spec.languageId);
        } catch (IOException e) {
            return null;
        }
    }

    public LspClient client() { return client; }

    public String currentUri() {
        synchronized (lock) { return uri; }
    }

    /** True when {@code diagnosticUri} refers to the document that is currently open. */
    public boolean isCurrent(String diagnosticUri) {
        String cur = currentUri();
        if (cur == null || diagnosticUri == null) return false;
        if (cur.equals(diagnosticUri)) return true;
        Path a = LspClient.pathOf(cur);
        Path b = LspClient.pathOf(diagnosticUri);
        return a != null && a.equals(b);
    }

    // ------------------------------------------------------------------
    // Document lifecycle (all of it runs on the sync thread)
    // ------------------------------------------------------------------

    /**
     * Opens the editor's CURRENT content as {@code file}. Call it after loading a file or after a
     * "save as" to a new name; an already open document is closed first. Returns immediately.
     */
    public void open(Path file) {
        run(() -> doOpen(file));
    }

    private void doOpen(Path file) {
        if (!client.awaitReady(60, TimeUnit.SECONDS)) return; // jdtls can take a while
        closeCurrent();

        String newUri = LspClient.uriOf(file);
        String text;
        // Snapshot + "start listening" must be atomic with respect to edits: an edit either is in the
        // snapshot (and dropped from the queue) or comes after it (and is queued), never both or neither.
        synchronized (editor) {
            text = editor.getFullText();
            synchronized (lock) {
                opened = true;
                uri = newUri;
                version = 1;
                pending = new ArrayList<>();
                changed = false;
            }
        }
        client.sendNotification("textDocument/didOpen", Json.obj("textDocument",
                Json.obj("uri", newUri, "languageId", languageId, "version", 1, "text", text)));
    }

    private void closeCurrent() {
        String old;
        synchronized (lock) {
            if (!opened) return;
            opened = false;
            old = uri;
            pending = new ArrayList<>();
            changed = false;
            if (flushTask != null) { flushTask.cancel(false); flushTask = null; }
        }
        client.sendNotification("textDocument/didClose", Json.obj("textDocument", Json.obj("uri", old)));
    }

    // ------------------------------------------------------------------
    // EditListener: called on the editor thread, must stay tiny
    // ------------------------------------------------------------------

    @Override
    public void onInsert(int line, int col, String text) {
        synchronized (lock) {
            if (!opened) return;
            if (client.syncKind() == 2) {
                pending.add(Json.obj("range", range(line, col, line, col), "text", text));
            }
            changed = true;
            scheduleFlushLocked();
        }
    }

    @Override
    public void onDelete(int startLine, int startCol, int endLine, int endCol) {
        synchronized (lock) {
            if (!opened) return;
            if (client.syncKind() == 2) {
                pending.add(Json.obj("range", range(startLine, startCol, endLine, endCol), "text", ""));
            }
            changed = true;
            scheduleFlushLocked();
        }
    }

    private void scheduleFlushLocked() {
        if (flushTask != null) flushTask.cancel(false); // debounce: restart the timer on every edit
        try {
            flushTask = exec.schedule(this::flush, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            flushTask = null; // shutting down
        }
    }

    private void flush() {
        List<Object> changes;
        String u;
        int ver;
        synchronized (lock) {
            flushTask = null;
            if (!opened || !changed) return;
            changed = false;
            changes = pending;
            pending = new ArrayList<>();
            u = uri;
            ver = ++version;
        }
        // Outside `lock`: getFullText() takes the editor monitor.
        List<Object> contentChanges = client.syncKind() == 2
                ? changes
                : Json.list(Json.obj("text", editor.getFullText()));
        client.sendNotification("textDocument/didChange", Json.obj(
                "textDocument", Json.obj("uri", u, "version", ver),
                "contentChanges", contentChanges));
    }

    private static Map<String, Object> range(int sl, int sc, int el, int ec) {
        return Json.obj(
                "start", Json.obj("line", sl, "character", sc),
                "end", Json.obj("line", el, "character", ec));
    }

    // ------------------------------------------------------------------
    // Shutdown
    // ------------------------------------------------------------------

    @Override
    public void close() {
        editor.removeChangeListener(this); // no-op if a newer sync already replaced us
        try {
            exec.execute(this::closeCurrent);
        } catch (RejectedExecutionException ignored) { }
        exec.shutdown();
        try {
            exec.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        client.close();
    }

    private void run(Runnable r) {
        try {
            exec.execute(r);
        } catch (RejectedExecutionException ignored) { }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Nearest ancestor containing a project marker (go.mod, .git, pom.xml...), else the file's folder. */
    static Path findRoot(Path file) {
        Path dir = file.getParent();
        for (Path p = dir; p != null; p = p.getParent()) {
            for (String marker : ROOT_MARKERS) {
                if (Files.exists(p.resolve(marker))) return p;
            }
        }
        return dir == null ? Paths.get(".").toAbsolutePath().normalize() : dir;
    }

    public void requestSuggestions(int line, int col) {
        if (!client.isAlive()) return;

        String uriStr = currentUri();
        if (uriStr == null) return;

        Map<String, Object> params = Json.obj(
                "textDocument", Json.obj("uri", uriStr),
                "position", Json.obj("line", line, "character", col)
        );

        client.request("textDocument/completion", params).whenComplete((result, error) -> {
            if (error != null || result == null) return;

            List<Object> items = null;
            if (result instanceof List) {
                items = Json.asList(result);
            } else if (result instanceof Map) {
                items = Json.asList(Json.asMap(result).get("items"));
            }

            if (items != null && !items.isEmpty()) {
                List<String> suggestions = new ArrayList<>();
                for (Object item : items) {
                    Map<String, Object> map = Json.asMap(item);
                    if (map != null) {
                        String label = Json.asString(map.get("label"));
                        if (label != null) suggestions.add(label);
                    }
                }
                showSuggestionsUi(suggestions);
            }
        });
    }
}
