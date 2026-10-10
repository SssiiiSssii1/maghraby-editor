# Lanterna Console Text Editor

A lightweight, terminal-based text editor built in Java using **Lanterna (TUI)**. The project features modal editing inspired by Vim/Nano, a custom Linked-List Stack for Undo/Redo operations, and in-place buffer cursor navigation.

---

## Features

- **Modal Editing Architecture:**
    - `NORMAL MODE`: Fast navigation, single-key shortcuts, and editor state control.
    - `INSERT MODE`: Direct typing and character-level editing with automatic word-wrapping.
    - `COMMAND MODE`: Extended command-line actions (`:w`, `:d`, `:f`, `:r`).
- **Custom Data Structures:**
    - Implemented custom `Node` and `MyStack` data structures (LIFO) to manage Undo and Redo states without relying on built-in Java collections.
- **In-place Cursor Movement:**
    - Full arrow-key navigation supporting 2D screen coordinate translation to 1D buffer indices.
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
| `Backspace` | In-place character deletion |
| `Enter` | New line |
| `Esc` | Return to **Normal Mode** |

### 3. Command Mode (`:`)
| Command | Usage | Description |
| --- | --- | --- |
| `:w <text>` | `:w Hello World` | Appends text to buffer |
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

Clone the repository and run using Maven:

```bash
git clone [https://github.com/mohammed1elmaghraby/lanterna-editor.git](https://github.com/mohammed1elmaghraby/lanterna-editor.git)
cd lanterna-editor
mvn clean compile
mvn exec:java -Dexec.mainClass="Main"