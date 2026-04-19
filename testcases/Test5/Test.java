// Entity -> Unit -> Warrior and Entity -> Tower -> CannonTower
class Entity {
    public int uid; // live
    public int typeCode; // die
    public int regionId; // die
}

class Unit extends Entity {
    public int hp; // live
    public int maxHp; // die
    public int defense; // die
    public int level; // die
}

class Warrior extends Unit {
    public int attack; // live
    public int stamina; // die
    public int rage; // die
}

class Tower extends Entity {
    public int range; // live
    public int cooldown; // die
    public int upgLevel; // die
}

class CannonTower extends Tower {
    public int damage; // live
    public int splashRadius; // die
    public int reloadTime; // die
    public int ammo; // die
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
    }
}
