class A {
    public int f1; // ACTIVE - used in E
    public int f2; // NOT used
}

class B extends A {
    public int f2; // ACTIVE - shadows A.f2, used via cast
    public int f3; // NOT used
}

class C extends B {
    public int f4; // NOT used
}

public class Test {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            C cObj = new C();

            // Use A.f1 through C
            cObj.f1 = i * 10;
            sum += cObj.f1;

            // Cast C to B to use B.f2 (shadows A.f2)
            B bObj = (B) cObj;
            bObj.f2 = i * 100;
            sum += bObj.f2;
        }
        System.out.println("Sum: " + sum);
        System.out.println("Expected: 330");
    }
}
