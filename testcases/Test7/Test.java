class A {
    // 40 bytes
    float f1, f2, f3, f4, f5, f6, f7, f8, f9, f10;
}

public class Test {
    public static void main(String[] args) {
        int iterations = 10_000_000;
        A[] keeper = new A[iterations];

        System.out.println("Starting allocations...");

        for (int i = 0; i < iterations; i++) {
            A obj = new A();
            obj.f1 = i * 1.5f; // only using f1
            keeper[i] = obj;
        }

        System.out.println("Allocations complete. Last value: " + keeper[iterations - 1].f1);

        try {
            System.out.println("jmap time");
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}