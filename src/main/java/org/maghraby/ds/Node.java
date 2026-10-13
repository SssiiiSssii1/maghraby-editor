package org.maghraby.ds;

/**
 * Updated Node to hold a single Delta action instead of the entire text snapshot.
 */
public class Node {
    EditAction data;
    Node next;

    public Node(EditAction data) {
        this.data = data;
        this.next = null;
    }
}