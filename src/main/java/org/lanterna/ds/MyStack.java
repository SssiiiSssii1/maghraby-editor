package org.lanterna.ds;

public class MyStack {
    private Node top;
    private int size = 0;
    // تم رفع الحد الأقصى لأن الـ Deltas تستهلك مساحة شبه معدومة مقارنة بالملفات
    private static final int MAX_CAPACITY = 1500;

    public void push(EditAction data) {
        if (size >= MAX_CAPACITY) {
            removeBottom();
        }
        Node newNode = new Node(data);
        newNode.next = top;
        top = newNode;
        size++;
    }

    public EditAction pop() {
        if (isEmpty()) return null;
        EditAction savedData = top.data;
        top = top.next;
        size--;
        return savedData;
    }

    public boolean isEmpty() {
        return top == null;
    }

    public void clear() {
        top = null;
        size = 0;
    }

    private void removeBottom() {
        if (top == null || top.next == null) return;
        Node current = top;
        while (current.next != null && current.next.next != null) {
            current = current.next;
        }
        current.next = null;
        size--;
    }
}