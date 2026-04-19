// Test10: DCE advantage - Ghost objects vs active objects
// Some objects have fields written but never read (ghosts)
// Some objects have fields written AND read (active)
class Metric {
    public int metricId; // live - accessed
    public int sourceId; // die - never accessed
    public int unitCode; // die - never accessed
}

class TimedMetric extends Metric {
    public int valueMs; // live - accessed
    public int intervalMs;// die - never accessed
    public int offsetMs; // die - never accessed
}

class AggMetric extends TimedMetric {
    public int count; // live - accessed
    public int minVal; // die - never accessed
    public int maxVal; // die - never accessed
    public int variance; // die - never accessed
    public int stdDev; // die - never accessed
}

public class Test {
    static final int ACTIVE_METRICS = 30;
    static final int GHOST_METRICS = 70;

    public static void main(String[] args) {
        long runningTotal = 0;

        // Ghost objects: allocated, fields written, but never read
        for (int i = 0; i < GHOST_METRICS; i++) {
            AggMetric ghost = new AggMetric();
            ghost.metricId = i;
            ghost.valueMs = i * 4;
            ghost.count = i % 10;
            // Fields are written but never read back
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
