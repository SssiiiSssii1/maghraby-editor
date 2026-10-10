/**
 * Represents a single node in a singly linked list.
 * Used as the building block for the custom Stack data structure.
 */
public class Node {
    String data; // The text state/snapshot stored in this node
    Node next;   // Reference to the next node in the chain

    /**
     * Initializes a new node with text data.
     * @param data The text state to store.
     */
    public Node(String data) {
        this.data = data;
        this.next = null;
    }
}