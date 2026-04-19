class A {
    public int f1; // yes
    public int f2; // no
}

class B extends A {
    public int f2; // yes
    public int f3; // no
}

class C extends B {
    public int f4; // no
}

public class Test {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            C cObj = new C();

            cObj.f1 = i * 10;
            sum += cObj.f1;

            B bObj = (B) cObj;
            bObj.f2 = i * 100;
            sum += bObj.f2;
        }
        System.out.println("Sum: " + sum);
    }
}
