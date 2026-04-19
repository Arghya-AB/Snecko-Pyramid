class Node {
    public int id; // live
    public int weight; // die
    public int color; // die
}

class GraphNode extends Node {
    public int degree; // live
    public int component; // die
    public int visited; // di
}

class WeightedNode extends GraphNode {
    public int edgeWeight; // live
    public int heuristic; // die
    public int potential; // die
    public int flow; // die
}

public class Test {
    public static void main(String[] args) {
        int n = 100;
        int totalDegree = 0;
        int maxEdge = 0;

        for (int i = 0; i < n; i++) {
            WeightedNode node = new WeightedNode();
            node.id = i;
            node.degree = i * 2;
            node.edgeWeight = i * 3 + 1;

            totalDegree += node.degree;
            if (node.edgeWeight > maxEdge) {
                maxEdge = node.edgeWeight;
            }
        }

        // Extra nodes that only use id
        for (int i = 0; i < 3; i++) {
            WeightedNode extra = new WeightedNode();
            extra.id = n + i;
            // degree and edgeWeight are never set or read on extras
        }

        System.out.println("Total degree: " + totalDegree);
        System.out.println("Max edge weight: " + maxEdge);
    }
}
