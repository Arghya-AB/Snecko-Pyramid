class A {
    // 40 bytes
    float f1, f2, f3, f4, f5, f6, f7, f8, f9, f10;
}

public class Test {
    public static void main(String[] args) {
        int iterations = 100000;
        A[] keeper = new A[iterations];

        System.out.println("Starting allocations...");

        for (int i = 0; i < iterations; i++) {
            A obj = new A();
            obj.f1 = i * 1.5f; // only using f1
            keeper[i] = obj;
        }

        System.out.println("Allocations complete. Last value: " + keeper[iterations - 1].f1);
    }
}