// Tes13: All fields active - Multi-level inheritance with full field usage
// Every field is accessed multiple times across all hierarchy levels

class Vehicle {
    public int id;       // ACTIVE
    public int year;     // ACTIVE
    public int speed;    // ACTIVE
}

class Car extends Vehicle {
    public int doors;    // ACTIVE
    public int seats;    // ACTIVE
    public int engine;   // ACTIVE
}

class SportsCar extends Car {
    public int turbo;    // ACTIVE
    public int nitro;    // ACTIVE
    public int rating;   // ACTIVE
}

public class Test {
    static final int ITERATIONS   = 20;
    static final int NUM_OBJECTS  = 10000000;


    public static void main(String[] args) {
        long total = 0;

        for (int obj = 1; obj <= NUM_OBJECTS; obj++) {
            SportsCar sc = new SportsCar();

            // Initialize every field
            sc.id     = obj;
            sc.year   = 2000 + obj;
            sc.speed  = obj * 10;
            sc.doors  = obj + 2;
            sc.seats  = obj + 3;
            sc.engine = obj * 100;
            sc.turbo  = obj * 5;
            sc.nitro  = obj * 7;
            sc.rating = obj * 3;

            // Access every field many times across ITERATIONS
            for (int i = 1; i <= ITERATIONS; i++) {
                // Read all Vehicle fields
                total += sc.id    * i;
                total += sc.year  - 2000;
                total += sc.speed / 10;

                // Read all Car fields
                total += sc.doors  * i;
                total += sc.seats  + i;
                total += sc.engine / 100;
                total += sc.turbo  * i;
                total += sc.nitro  - obj;
                total += sc.rating * i;

                total += (sc.speed + sc.turbo + sc.nitro) / (sc.doors + 1);
                total += (sc.engine / 100) * (sc.seats - 2);
                total += sc.id * sc.rating + sc.year % 10;
            }
        }

        System.out.println("Total: " + total);
        long expected = 0;
        for (int obj = 1; obj <= NUM_OBJECTS; obj++) {
            int id = obj, year = 2000+obj, speed = obj*10;
            int doors = obj+2, seats = obj+3, engine = obj*100;
            int turbo = obj*5, nitro = obj*7, rating = obj*3;
            for (int i = 1; i <= ITERATIONS; i++) {
                expected += id*i + (year-2000) + speed/10;
                expected += doors*i + seats+i + engine/100;
                expected += turbo*i + nitro-obj + rating*i;
                expected += (speed+turbo+nitro)/(doors+1);
                expected += (engine/100)*(seats-2);
                expected += id*rating + year%10;
            }
        }
        System.out.println("Expected: " + expected);
        System.out.println("Match: " + (total == expected));
    }
}