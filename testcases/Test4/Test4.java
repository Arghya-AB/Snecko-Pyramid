class A {
    static int f2;
    public int f1;
    public void set_f1() {
        this.f1 = 10;
    }
}
class B extends A {
    public int f2;
    public void set_f2() {
        this.f2 = 12;
    }
}
class C extends B {
    public int f3;
    public void set_f3() {
        this.f3 = -15;
    }
}
public class Test4 {
    public static void main(String[] args) {
        C obj = new C();
        obj.set_f2();
        C obj2 = new C();
        obj2.set_f1();
        obj2.set_f2();
        obj2.set_f3();
    }
} 