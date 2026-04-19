// Multi level inheritance with two versions of original class being created.

class Item {
    public int id;       // live
    public int discount; // die
    public int category; // die
}

class Product extends Item {
    public int stock;    // live
    public int shelf;    // die
    public int batch;    // die
}

class ElectronicProduct extends Product {
    public int price;     // live
    public int warranty;  // die
    public int voltage;   // die
    public int frequency; // die
}

public class Test {
    public static void main(String[] args) {
        int n = 1000000;
        int totalStock = 0;
        int maxPrice = 0;

        // While loop counting down
        int i = n - 1;
        while (i >= 0) {
            ElectronicProduct product = new ElectronicProduct();
            product.id = i;
            product.stock = i * 2;
            product.price = i * 3 + 1;
            totalStock += product.stock;
            if (product.price > maxPrice) {
                maxPrice = product.price;
            }
            i--;
        }

        // Do-while for the extra products
        int j = 0;
        do {
            ElectronicProduct extra = new ElectronicProduct();
            extra.id = n + j;
            // stock and price are never set or read on extras
            j++;
        } while (j < n);

        System.out.println("Total stock: " + totalStock);
        System.out.println("Max price: " + maxPrice);
    }
}