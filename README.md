# maghraby-editor

A lightweight, zero-dependency, modal terminal text editor built entirely from scratch in pure Java. Designed for speed and minimal memory footprint, featuring native ANSI terminal rendering, a multi-threaded engine, multi-language syntax highlighting, an integrated code execution runner, and a highly optimized **Piece Table** core architecture.

---

## Key Architectural Highlights

- **Piece Table Data Structure (v2.0 Engine):** The core buffer engine utilizes a Piece Table architecture (`originalBuffer`, `addBuffer`, and `pieces`). This guarantees near **O(1) time complexity** for text insertions and deletions, making the editor blazing fast and highly memory-efficient even when handling massive files.
- **Zero External Dependencies:** Built with pure Java and direct POSIX terminal control via ANSI escape codes and `stty`. No third-party UI libraries.
- **Multi-Threaded Architecture:**
  - **Input Thread:** Non-blocking raw byte parsing and escape sequence decoding (CSI/SS3 sequences).
  - **Render Thread:** Smooth, decoupled rendering loop (~60 FPS) with horizontal/vertical viewport clipping.
  - **Resize Watcher Daemon:** Background terminal size detection with dynamic relayout.
- **Memory-Efficient Delta Undo/Redo:**
  - Uses an action-based delta model (`EditAction`) storing only individual microscopic edits instead of heavy full-buffer snapshots.
  - Bounded custom LIFO stack (up to 1500 actions) preventing memory leaks during long coding sessions.
- **Safe Code Runner (`:run` / `:compile`):**
  - Compiles and runs **Java**, **C**, **C++**, and **Python**.
  - Uses isolated temporary build directories to keep user workspaces clean of `.class` or binary artifacts.
  - Employs signal guard protection (`SigintGuard`) via Java reflection to prevent `Ctrl+C` from closing the editor while running user programs.

---

## Editor Controls & Keybindings

### 1. Normal Mode (Default)
| Key | Action |
| --- | --- |
| `i` | Enter **Insert Mode** |
| `:` | Enter **Command Mode** |
| `u` | **Undo** last change |
| `r` | **Redo** reverted change |
| `F5` | Run / Compile current file |
| `Ctrl + S` | Quick save |
| `Ctrl + Q` | Force quit |
| `Arrow Keys` / `Home` / `End` / `PgUp` / `PgDn` | Cursor navigation |
| `q` | Safe quit (warns if unsaved) |

### 2. Insert Mode
| Key | Action |
| --- | --- |
| `Typing` | In-place character insertion |
| `Tab` | 4-space tab indentation |
| `Backspace` | In-place character deletion before cursor |
| `Delete` | Forward character deletion |
| `Enter` | New line insertion |
| `Esc` | Return to **Normal Mode** |

### 3. Command Mode (`:`)
| Command | Usage | Description |
| --- | --- | --- |
| `:w [filename]` | `:w` or `:w app.java` | Save buffer to current or new file |
| `:wq` / `:x` | `:wq` | Save and quit |
| `:q` | `:q` | Quit (checks for unsaved changes) |
| `:q!` | `:q!` | Force quit discarding changes |
| `:run [args]` | `:run arg1 arg2` | Compile and run active file with terminal handover |
| `:compile` / `:build` | `:compile` | Compile / syntax check without running |
| `:help` / `:h` | `:help` | Show command assistance |

---

## Supported Compilers & Runtimes

- **Java:** `javac` & `java` (automatic package declaration detection and source-root calculation)
- **C:** `gcc`, `clang`, or `cc` (auto-links `-lm`)
- **C++:** `g++`, `clang++`, or `c++`
- **Python:** `python3`, `python`, or `py` (in-memory syntax validation)

---

## Requirements

- **OS:** Linux, macOS, WSL, or Git-Bash (requires a POSIX terminal supporting `stty` and ANSI escapes)
- **Java:** JDK 17+
- **Build Tool:** Apache Maven

---

## Build & Run

Clone the repository:
```bash
git clone https://github.com/mohammed1elmaghraby/maghraby-editor.git
cd maghraby-editor
```
Build the JAR:

```bash
mvn clean package
```
Run directly:

```bash
java -jar target/maghraby-editor-2.1.0.jar [filename]
```
Engineered with ❤️ by Mohammed Elmaghraby.