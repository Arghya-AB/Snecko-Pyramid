public class Test {
    int x;
    Test f;
    public static void main (String[] args) {
        Test o1;
        if(args.length > 0 ) {
            o1 = new A();
        } else {
            o1 = new Test();
        }
        o1.foo();
    }
    void foo() {
        Test o2 = new Test();
        Test o3 = new Test();
        o2.x = 10;
        o3.x = 20;
        int y = o2.x + o3.x;
        o3.x = y;
        o2.f = new Test();
    }
}
class A extends Test{
    void foo() {
        Test o2 = new Test();
        o2.x = 120;
        o2.f = new A();
    }
}

