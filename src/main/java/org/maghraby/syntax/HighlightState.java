package org.maghraby.syntax;

/**
 * Tracks parser state across lines to maintain multi-line token integrity.
 */
public enum HighlightState {
    NORMAL,
    C_BLOCK_COMMENT,
    PY_TRIPLE_DOUBLE,
    PY_TRIPLE_SINGLE
}