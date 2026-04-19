class Entity {
    public int uid;       // live - written and read
    public int typeCode;  // die - never accessed
    public int regionId;  // die - never accessed
}

class Unit extends Entity {
    public int hp;        // live - written and read
    public int maxHp;     // die - never accessed
    public int defense;   // die - never accessed
    public int level;     // die - never accessed
}

class Warrior extends Unit {
    public int attack;    // live - written and read
    public int stamina;   // die - never accessed
    public int rage;      // die - never accessed
}

class Tower extends Entity {
    public int range;     // live - written and read
    public int cooldown;  // die - never accessed
    public int upgLevel;  // die - never accessed
}

class CannonTower extends Tower {
    public int damage;    // live - written and read
    public int splashRadius; // die - never accessed
    public int reloadTime;   // die - never accessed
    public int ammo;         // die - never accessed
}

public class Test {
    static final int NUM_WARRIORS = 50000;   // adjust: number of Warrior objects
    static final int NUM_TOWERS = 10000;     // adjust: number of CannonTower objects
    static final int SIM_ROUNDS = 5;      // adjust: number of simulation rounds

    public static void main(String[] args) {
        Warrior[] warriors = new Warrior[NUM_WARRIORS];
        for (int i = 0; i < NUM_WARRIORS; i++) {
            warriors[i] = new Warrior();
            warriors[i].uid = i;
            warriors[i].hp = 100 + (i % 50);
            warriors[i].attack = 10 + (i % 20);
        }

        CannonTower[] towers = new CannonTower[NUM_TOWERS];
        for (int i = 0; i < NUM_TOWERS; i++) {
            towers[i] = new CannonTower();
            towers[i].uid = NUM_WARRIORS + i;
            towers[i].range = 5 + (i % 10);
            towers[i].damage = 30 + (i % 15);
        }

        long totalDamage = 0;
        for (int round = 0; round < SIM_ROUNDS; round++) {
            for (int t = 0; t < NUM_TOWERS; t++) {
                totalDamage += towers[t].damage;
            }
        }

        int survivingHp = 0;
        for (int i = 0; i < NUM_WARRIORS; i++) {
            survivingHp += warriors[i].hp;
        }

        System.out.println("Total damage dealt: " + totalDamage);
        System.out.println("Total surviving HP: " + survivingHp);
    }
}