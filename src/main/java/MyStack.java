public class MyStack {
    private Node top;

    public void push(String data) {
        Node newNode = new Node(data);
        newNode.next = top;
        top = newNode;
    }

    public String pop() {
        if (isEmpty()) return null;

        String savedData = top.data;
        top = top.next;
        return savedData;
    }

    public boolean isEmpty() {
        return top == null;
    }

    public void clear() {
        top = null;
    }
}