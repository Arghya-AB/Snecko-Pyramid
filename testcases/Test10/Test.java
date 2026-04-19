class Metric {
    public int metricId;  // live - written and read
    public int sourceId;  // die - never accessed
    public int unitCode;  // die - never accessed
}

class TimedMetric extends Metric {
    public int valueMs;   // live - written and read
    public int intervalMs;// die - never accessed
    public int offsetMs;  // die - never accessed
}

class AggMetric extends TimedMetric {
    public int count;     // live - written and read
    public int minVal;    // die - never accessed
    public int maxVal;    // die - never accessed
    public int variance;  // die - never accessed
    public int stdDev;    // die - never accessed
}

public class Test {
    static final int ACTIVE_METRICS = 300;   // adjust: metrics whose fields are read back
    static final int GHOST_METRICS = 700;    // adjust: metrics allocated but never read from
    static final int REPORT_EVERY = 50;      // adjust: print a subtotal every N active metrics

    public static void main(String[] args) {
        // Ghost objects: allocated, fields written, but never read
        AggMetric[] ghosts = new AggMetric[GHOST_METRICS];
        for (int i = 0; i < GHOST_METRICS; i++) {
            ghosts[i] = new AggMetric();
            ghosts[i].metricId = i;
            ghosts[i].valueMs = i * 4;
            ghosts[i].count = i % 10;
        }

        // Active objects: fields written AND read
        AggMetric[] active = new AggMetric[ACTIVE_METRICS];
        for (int i = 0; i < ACTIVE_METRICS; i++) {
            active[i] = new AggMetric();
            active[i].metricId = GHOST_METRICS + i;
            active[i].valueMs = i * 11;
            active[i].count = 1 + (i % 8);
        }

        long runningTotal = 0;
        for (int i = 0; i < ACTIVE_METRICS; i++) {
            runningTotal += (long) active[i].valueMs * active[i].count;
            if ((i + 1) % REPORT_EVERY == 0) {
                System.out.println("Subtotal at " + active[i].metricId + ": " + runningTotal);
            }
        }

        System.out.println("Final total: " + runningTotal);
    }
}