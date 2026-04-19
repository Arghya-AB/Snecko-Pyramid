// Test10: DCE advantage - Ghost objects vs active objects
// Some objects have fields written but never read
// Some objects have fields written AND read
class Metric {
    public int metricId; // live
    public int sourceId; // die
    public int unitCode; // die
}

class TimedMetric extends Metric {
    public int valueMs; // live
    public int intervalMs;// die
    public int offsetMs; // die
}

class AggMetric extends TimedMetric {
    public int count; // live
    public int minVal; // die
    public int maxVal; // die
    public int variance; // die
    public int stdDev; // die
}

public class Test {
    static final int ACTIVE_METRICS = 100000000;
    static final int GHOST_METRICS = 100000000;

    public static void main(String[] args) {
        long runningTotal = 0;

        // Ghost objects: allocated, fields written, but never read
        for (int i = 0; i < GHOST_METRICS; i++) {
            AggMetric ghost = new AggMetric();
            ghost.metricId = i;
            ghost.valueMs = i * 4;
            ghost.count = i % 10;
        }

        // Active objects: fields written AND read
        for (int i = 0; i < ACTIVE_METRICS; i++) {
            AggMetric active = new AggMetric();
            active.metricId = GHOST_METRICS + i;
            active.valueMs = i * 11;
            active.count = 1 + (i % 8);

            runningTotal += (long) active.valueMs * active.count;
        }

        System.out.println("Final total: " + runningTotal);
    }
}
