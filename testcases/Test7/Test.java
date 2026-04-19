// Test11: DCE advantage - Multi-level inheritance with selective field usage
// Fields accessed through different levels of hierarchy
class Shape {
    public int id; // ACTIVE 
    public int type; // DIE 
}

class Rectangle extends Shape {
    public int width; // ACTIVE 
    public int height; // ACTIVE
    public int area; // DIE
}

class Square extends Rectangle {
    public int side; // ACTIVE
    public int rotation; // DIE
}

public class Test {
    public static void main(String[] args) {
        int total = 0;
        for (int i = 1; i <= 30000000; i++) {
            Square sq = new Square();
            sq.id = i;           
            sq.width = i * 10;   
            sq.height = i * 10;  
            sq.side = i;         
            total += sq.id + sq.width + sq.height + sq.side;
        }
        System.out.println("Total: " + total);
        System.out.println("Expected: 132");
    }
}
