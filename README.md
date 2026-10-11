# Lanterna Console Text Editor

A lightweight, terminal-based text editor built in Java using **Lanterna (TUI)**. The project features modal editing inspired by Vim, extensible multi-language syntax highlighting, a custom Linked-List Stack for Undo/Redo operations, in-place buffer cursor navigation, and full file persistence.

---

## Features

- **Modal Editing Architecture:**
  - `NORMAL MODE`: Fast navigation, single-key shortcuts, and editor state control.
  - `INSERT MODE`: Direct typing and character-level editing with automatic line handling.
  - `COMMAND MODE`: Extended command-line actions (`:w`, `:d`, `:f`, `:r`, `:load`, `:run`).
- **Extensible Syntax Highlighting:**
  - Pluggable Strategy Pattern supporting syntax coloring for **Java**, **C/C++**, and **Python**.
  - Highlights keywords, function invocations, string literals, numbers, and multi-line comments.
  - Modular `HighlighterRegistry` supporting easy addition of new languages.
- **Integrated Code Execution:**
  - Directly compile and run active buffers (`C++`, `C`, `Java`, `Python`, `Bash`) via `:run` / `:c` without exiting the editor.
- **Custom Data Structures:**
  - Handcrafted `Node` and `MyStack` data structures (LIFO via Singly Linked List) to manage Undo and Redo states without relying on built-in Java collections.
- **In-place Cursor Movement:**
  - Full arrow-key navigation with dynamic 2D screen coordinate translation to 1D buffer indices (Single Source of Truth).
- **File Persistence & CLI Integration:**
  - Load and save source files directly.
  - Open files instantly via command-line arguments.
- **Buffer Operations:**
  - Substring search (`find`), string replacement (`replace`), and character/word deletion (`delete`).

---

## Editor Controls & Keybindings

### 1. Normal Mode (Default)
| Key | Action |
| --- | --- |
| `i` | Enter **Insert Mode** |
| `:` | Enter **Command Mode** |
| `u` | **Undo** last change |
| `Ctrl + R` | **Redo** reverted change |
| `Arrow Keys` | Move cursor around text |
| `q` | Quit editor |

### 2. Insert Mode
| Key | Action |
| --- | --- |
| `Type any text` | In-place character insertion |
| `Backspace` | In-place character deletion (before cursor) |
| `Delete` | In-place character deletion (at cursor) |
| `Enter` | New line |
| `Esc` | Return to **Normal Mode** |

### 3. Command Mode (`:`)
| Command | Usage | Description |
| --- | --- | --- |
| `:w` | `:w` | Quick save to current file |
| `:w <filename>` | `:w notes.txt` | Saves buffer to specified file |
| `:load <filename>` | `:load main.cpp` | Loads text from file into buffer |
| `:run` or `:c` | `:run` | Compiles and executes current source code |
| `:d <count>` | `:d 5` | Deletes last *N* characters |
| `:d <word>` | `:d test` | Deletes first occurrence of word |
| `:f <word>` | `:f target` | Finds word index |
| `:r <old> <new>` | `:r foo bar` | Replaces target word |
| `:exit` or `:q` | `:q` | Exits the editor |

---

## Requirements

- Java JDK 17+ (or JDK 21 / 25)
- Apache Maven

---

## Build & Run

Clone the repository:

```bash
git clone [https://github.com/mohammed1elmaghraby/lanterna-editor.git](https://github.com/mohammed1elmaghraby/lanterna-editor.git)
cd lanterna-editor
```

Package the executable Fat-JAR:
```bash
mvn clean package
```

Run with a new empty buffer:
```bash
mvn exec:java -Dexec.mainClass="org.lanterna.Main"
```

Or open an existing / new file directly:
```bash
mvn exec:java -Dexec.mainClass="org.lanterna.Main" -Dexec.args="Main.java"
```

Or execute the packaged shaded JAR directly:
```bash
java -jar target/lanterna-editor-1.0-SNAPSHOT.jar [filename]
```

Developed with ❤️ by Mohammed Elmaghraby.