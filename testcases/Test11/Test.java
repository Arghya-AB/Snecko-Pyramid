// Test11: DCE advantage - Multi-level inheritance with selective field usage
// Fields accessed through different levels of hierarchy
class Shape {
    public int id; // ACTIVE - used through all levels
    public int type; // DIE - never accessed
}

class Rectangle extends Shape {
    public int width; // ACTIVE - used
    public int height; // ACTIVE - used
    public int area; // DIE - not used
}

class Square extends Rectangle {
    public int side; // ACTIVE - used (redundant with w/h but used)
    public int rotation; // DIE - not used
}

public class Test {
    public static void main(String[] args) {
        int total = 0;
        for (int i = 1; i <= 3; i++) {
            Square sq = new Square();
            sq.id = i;           // Access through Square
            sq.width = i * 10;   // Access inherited field
            sq.height = i * 10;  // Access inherited field
            sq.side = i;         // Access own field
            total += sq.id + sq.width + sq.height + sq.side;
        }
        System.out.println("Total: " + total);
        System.out.println("Expected: 132");
    }
}
