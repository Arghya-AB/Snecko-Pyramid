class A {
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
    public C f4;
    public int f5;
    public int f6;
    public int f7;
    public void set_f3() {
        this.f3 = -15;
    }
    public void set_f3(B input) {
        this.f3 = 156;
    }
}
public class Test {
    public static void main(String[] args) {
        C obj = new C();
        obj.set_f2();
        C obj2 = new C();
        obj2.set_f1();
        obj2.set_f2();
        obj2.set_f3();
        obj2.f4 = obj2;
        obj2.f5 = 2;
        obj2.f6 = 2;
        obj2.f7 = 2;
        C obj3 = new C();
        obj3.set_f1();
        obj3.set_f2();
        obj3.set_f3();
        obj3.f4 = obj3;
        obj3.f5 = 2;
        obj3.f6 = 2;
        obj3.f7 = 2;
    }
} 