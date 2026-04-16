class A {
    int f1;
    int f2;
    public void createA() {
        B obj = new B();
        obj.f1 = 2;
    }
}

class B {
    int f1;
    int f2;
    // public void createA() {

    // }
}
public class Test5 {
    public static void main(String[] args) {
        A obj = new A();
        obj.f2 = 16;
        A obj2 = new A();
        obj2.f1 = 16;
        obj.createA();
    }
}
