// Test8: DCE advantage - Arrays and objects with unused fields
// Array of objects where only some fields are accessed
class DataItem {
    public int key; // ACTIVE - used
    public int value; // ACTIVE - used
    public int hash; // DIE - computed but never read
    public int flags; // DIE - not used
    public DataItem next; // DIE - not used
}

class DataContainer {
    public DataItem[] items; // ACTIVE - used
    public int count; // DIE - not used
    public int capacity; // DIE - not used
}

public class Test {
    public static void main(String[] args) {
        DataContainer container = new DataContainer();
        container.items = new DataItem[10000000];

        int sum = 0;
        for (int i = 0; i < 10000000; i++) {
            DataItem item = new DataItem();
            item.key = i;
            item.value = i * i;
            container.items[i] = item;
            sum += item.key + item.value;
        }

        // Access through array
        int arraySum = 0;
        for (int i = 0; i < 10000000; i++) {
            arraySum += container.items[i].key + container.items[i].value;
        }

        System.out.println("Direct sum: " + sum);
        System.out.println("Array sum: " + arraySum);
        System.out.println("Expected: 60 for both");
    }
}
