public class Test2 {
    int f1;
    int f2;
    Test2 f3;
    public static void main(String[] args) {
        Test2 obj = new Test2();
        obj.f2 = 5;
    }
    public void change_f2() {
        this.f2 = 5;
    }
    public void change_f1_f3() {
        this.f1 = 5;
        this.f3 = new Test2();
    }
}
