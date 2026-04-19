// Test9: DCE advantage - Multiple class hierarchies with direct field access
// Entity -> Unit -> Warrior and Entity -> Tower -> CannonTower
class Entity {
    public int uid; // live - accessed
    public int typeCode; // die - never accessed
    public int regionId; // die - never accessed
}

class Unit extends Entity {
    public int hp; // live - accessed
    public int maxHp; // die - never accessed
    public int defense; // die - never accessed
    public int level; // die - never accessed
}

class Warrior extends Unit {
    public int attack; // live - accessed
    public int stamina; // die - never accessed
    public int rage; // die - never accessed
}

class Tower extends Entity {
    public int range; // live - accessed
    public int cooldown; // die - never accessed
    public int upgLevel; // die - never accessed
}

class CannonTower extends Tower {
    public int damage; // live - accessed
    public int splashRadius; // die - never accessed
    public int reloadTime; // die - never accessed
    public int ammo; // die - never accessed
}

public class Test {
    static final int NUM_WARRIORS = 10000;
    static final int NUM_TOWERS = 500;
    static final int SIM_ROUNDS = 50;

    public static void main(String[] args) {
        long totalDamage = 0;
        int survivingHp = 0;

        // Create warriors
        for (int i = 0; i < NUM_WARRIORS; i++) {
            Warrior w = new Warrior();
            w.uid = i;
            w.hp = 100 + (i % 50);
            w.attack = 10 + (i % 20);
            survivingHp += w.hp;
        }

        // Create towers and calculate damage
        for (int i = 0; i < NUM_TOWERS; i++) {
            CannonTower t = new CannonTower();
            t.uid = NUM_WARRIORS + i;
            t.range = 5 + (i % 10);
            t.damage = 30 + (i % 15);

            for (int round = 0; round < SIM_ROUNDS; round++) {
                totalDamage += t.damage;
            }
        }

        System.out.println("Total damage dealt: " + totalDamage);
        System.out.println("Total surviving HP: " + survivingHp);
        System.out.println("Expected: damage=8875, hp=7450");
    }
}
