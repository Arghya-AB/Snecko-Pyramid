class TestCase1_Branch_Node {
    TestCase1_Branch_Node next;
}

public class TestCase1_Branch {
    public static void main(String[] args) {
        TestCase1_Branch_Node a = new TestCase1_Branch_Node();
        TestCase1_Branch_Node b;

        if (args.length > 0) {
            b = new TestCase1_Branch_Node();
            a.next = b;
        } else {
            b = new TestCase1_Branch_Node();
            a.next = b;
        }

        TestCase1_Branch_Node c = a.next;
        var p = c.next;
    }
}
