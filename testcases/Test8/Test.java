class Node {
    public int id;       // live - read and written
    public int weight;   // die - never accessed
    public int color;    // die - never accessed
}

class GraphNode extends Node {
    public int degree;    // live - read and written
    public int component; // die - never accessed
    public int visited;   // die - never accessed
}

class WeightedNode extends GraphNode {
    public int edgeWeight; // live - read and written
    public int heuristic;  // die - never accessed
    public int potential;  // die - never accessed
    public int flow;       // die - never accessed
}

public class Test {
    public static void main(String[] args) {
        int n = 100000;
        WeightedNode[] nodes = new WeightedNode[n];

        for (int i = 0; i < n; i++) {
            nodes[i] = new WeightedNode();
            nodes[i].id = i;
            nodes[i].degree = i * 2;
            nodes[i].edgeWeight = i * 3 + 1;
        }

        // Separately allocate some extra nodes that are barely used
        WeightedNode[] extras = new WeightedNode[3];
        for (int i = 0; i < extras.length; i++) {
            extras[i] = new WeightedNode();
            extras[i].id = n + i;
            // degree and edgeWeight are never set or read on extras
        }

        int totalDegree = 0;
        int maxEdge = 0;
        for (int i = 0; i < n; i++) {
            totalDegree += nodes[i].degree;
            if (nodes[i].edgeWeight > maxEdge) {
                maxEdge = nodes[i].edgeWeight;
            }
        }

        System.out.println("Total degree: " + totalDegree);
        System.out.println("Max edge weight: " + maxEdge);
    }
}