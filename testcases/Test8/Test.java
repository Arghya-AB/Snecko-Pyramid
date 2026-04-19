// Test8: DCE advantage - 3-level hierarchy with direct field access
// Some fields live, some dead
class Node {
    public int id; // live - accessed
    public int weight; // die - never accessed
    public int color; // die - never accessed
}

class GraphNode extends Node {
    public int degree; // live - accessed
    public int component; // die - never accessed
    public int visited; // die - never accessed
}

class WeightedNode extends GraphNode {
    public int edgeWeight; // live - accessed
    public int heuristic; // die - never accessed
    public int potential; // die - never accessed
    public int flow; // die - never accessed
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
        System.out.println("Expected: degree=9900, maxEdge=298");
    }
}
