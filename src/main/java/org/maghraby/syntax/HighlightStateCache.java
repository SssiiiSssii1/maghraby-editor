package org.maghraby.syntax;

import java.util.Arrays;
import java.util.List;

/**
 * Remembers the highlighter state at the start of every line that has been scanned so far, so the
 * renderer can begin highlighting at the first <i>visible</i> line instead of re-scanning the whole
 * file from line 0 on every frame.
 *
 * State at the start of line N depends only on lines 0..N-1, so an edit on line D invalidates the
 * states of lines D+1 and below but not the state at the start of D itself.
 *
 * Not thread-safe: use it from a single thread (the render thread).
 */
public final class HighlightStateCache {

    /** Supplies document lines [from, to), clamped to the document (TextEditor::getLines). */
    public interface LineSource {
        List<String> lines(int from, int to);
    }

    private static final HighlightState[] STATES = HighlightState.values();
    private static final int CHUNK = 512; // lines fetched per call while catching up

    private byte[] states = new byte[1024]; // states[i] = ordinal of the state at the start of line i
    private int valid = 1;                  // states[0 .. valid) can be trusted; line 0 always starts NORMAL
    private SyntaxHighlighter highlighter;

    public HighlightStateCache() {
        states[0] = (byte) HighlightState.NORMAL.ordinal();
    }

    /** Switching highlighter (e.g. the file was renamed from .txt to .java) discards everything. */
    public void useHighlighter(SyntaxHighlighter h) {
        if (h != highlighter) {
            highlighter = h;
            valid = 1;
        }
    }

    /** An edit touched {@code line}: states at the start of lines after it are stale. */
    public void invalidateFrom(int line) {
        valid = Math.max(1, Math.min(valid, line + 1));
    }

    /**
     * State at the start of {@code line}. Scans only the lines between the last known state and
     * {@code line}; repeated calls for the same or an earlier line are O(1).
     */
    public HighlightState stateAt(int line, LineSource source) {
        if (highlighter == null) throw new IllegalStateException("call useHighlighter() first");
        if (line < 0) line = 0;
        if (line < valid) return STATES[states[line]];

        int cur = valid - 1; // the state at the start of this line is known
        HighlightState state = STATES[states[cur]];

        while (cur < line) {
            int want = Math.min(line - cur, CHUNK);
            List<String> chunk = source.lines(cur, cur + want);
            if (chunk.isEmpty()) break; // document shorter than requested; keep what we have

            for (String text : chunk) {
                state = highlighter.highlightLine(text, state).nextState;
                cur++;
                ensureCapacity(cur + 1);
                states[cur] = (byte) state.ordinal();
                valid = cur + 1;
            }
        }
        return state;
    }

    private void ensureCapacity(int needed) {
        if (needed > states.length) {
            states = Arrays.copyOf(states, Math.max(needed, states.length * 2));
        }
    }
}