class DataClass {
    public int field1; // die
    public int field2; // die
    public int field3; // die
    public int field4; // die
    public int field5; // live

    public DataClass() {
        this.field5 = 42;
    }

    public int getField5() {
        return this.field5;
    }
}

public class Test {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 0; i < 10000; i++) {
            DataClass obj = new DataClass();
            sum += obj.getField5();
        }
        System.out.println("Sum of field5 values: " + sum);
        System.out.println("Expected: 210 (5 * 42)");
    }
}
