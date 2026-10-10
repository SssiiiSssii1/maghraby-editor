/**
 * Custom LIFO (Last-In-First-Out) stack implementation using a singly linked list.
 * Specifically used for managing history states in Undo and Redo operations.
 */
public class MyStack {
    private Node top; // Pointer to the top element of the stack

    /**
     * Pushes a new text snapshot onto the top of the stack.
     * @param data The text state to save.
     */
    public void push(String data) {
        Node newNode = new Node(data);
        newNode.next = top;
        top = newNode;
    }

    /**
     * Pops and returns the most recent text snapshot from the stack.
     * @return The popped string data, or null if the stack is empty.
     */
    public String pop() {
        if (isEmpty()) return null;

        String savedData = top.data;
        top = top.next;
        return savedData;
    }

    /**
     * Checks whether the stack contains any elements.
     * @return True if the stack is empty, false otherwise.
     */
    public boolean isEmpty() {
        return top == null;
    }

    /**
     * Clears all elements from the stack (used when a new action breaks the redo history).
     */
    public void clear() {
        top = null;
    }
}