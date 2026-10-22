import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Graph implementations for Assignment 3. All state is per request. */
public final class Algorithms {
    private Algorithms() { }

    private static final class UnionFind {
        final int[] parent;
        final int[] size;
        int components;
        int largest;

        UnionFind(int n) {
            parent = new int[n];
            size = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                size[i] = 1;
            }
            components = n;
            largest = n == 0 ? 0 : 1;
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]; // Path halving.
                x = parent[x];
            }
            return x;
        }

        boolean union(int a, int b) {
            a = find(a);
            b = find(b);
            if (a == b) return false;
            if (size[a] < size[b]) {
                int tmp = a; a = b; b = tmp;
            }
            parent[b] = a;
            size[a] += size[b];
            components--;
            largest = Math.max(largest, size[a]);
            return true;
        }
    }

    static Map<String, Object> dsu(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] operations = Input.operations(input);
        UnionFind uf = new UnionFind(n);
        List<int[]> states = new ArrayList<>(operations.length);
        for (int[] op : operations) {
            uf.union(op[0], op[1]);
            states.add(new int[]{uf.components, uf.largest});
        }
        return Map.of("states", states);
    }

    static Map<String, Object> kruskal(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        Integer[] sortedIds = new Integer[edges.length];
        for (int id = 0; id < edges.length; id++) sortedIds[id] = id;
        Arrays.sort(sortedIds, (a, b) -> {
            int byWeight = Integer.compare(edges[a][2], edges[b][2]);
            return byWeight != 0 ? byWeight : Integer.compare(a, b);
        });
        UnionFind uf = new UnionFind(n);
        List<Integer> selected = new ArrayList<>();
        long cost = 0L;
        for (int id : sortedIds) {
            int[] e = edges[id];
            if (uf.union(e[0], e[1])) {
                selected.add(id);
                cost += (long) e[2];
            }
        }
        return Map.of("cost", cost, "edge_ids", selected, "components", uf.components);
    }

    private static final class HeapEdge {
        final int to;
        final int weight;
        final int id;
        HeapEdge(int to, int weight, int id) {
            this.to = to;
            this.weight = weight;
            this.id = id;
        }
    }

    static Map<String, Object> prim(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        int m = edges.length;
        int[] head = new int[n];
        Arrays.fill(head, -1);
        int[] to = new int[2 * m];
        int[] next = new int[2 * m];
        int[] edgeId = new int[2 * m];
        int p = 0;
        for (int id = 0; id < m; id++) {
            int u = edges[id][0], v = edges[id][1];
            to[p] = v; edgeId[p] = id; next[p] = head[u]; head[u] = p++;
            to[p] = u; edgeId[p] = id; next[p] = head[v]; head[v] = p++;
        }
        PriorityQueue<HeapEdge> pq = new PriorityQueue<>(
                Comparator.comparingInt((HeapEdge e) -> e.weight).thenComparingInt(e -> e.id));
        boolean[] visited = new boolean[n];
        List<Integer> selected = new ArrayList<>();
        long cost = 0L;
        int components = 0;
        for (int start = 0; start < n; start++) {
            if (visited[start]) continue;
            components++;
            visited[start] = true;
            for (int a = head[start]; a != -1; a = next[a]) {
                if (!visited[to[a]]) pq.add(new HeapEdge(to[a], edges[edgeId[a]][2], edgeId[a]));
            }
            while (!pq.isEmpty()) {
                HeapEdge best = pq.poll();
                if (visited[best.to]) continue;
                int v = best.to;
                visited[v] = true;
                cost += (long) best.weight;
                selected.add(best.id);
                for (int a = head[v]; a != -1; a = next[a]) {
                    if (!visited[to[a]]) pq.add(new HeapEdge(to[a], edges[edgeId[a]][2], edgeId[a]));
                }
            }
        }
        return Map.of("cost", cost, "edge_ids", selected, "components", components);
    }

    /** Adjacency with original edge IDs, represented as primitive arrays. */
    private static final class DirectedGraph {
        final int[] head;
        final int[] next;
        final int[] to;
        DirectedGraph(int n, int[][] edges, boolean transpose) {
            head = new int[n];
            Arrays.fill(head, -1);
            next = new int[edges.length];
            to = new int[edges.length];
            for (int i = 0; i < edges.length; i++) {
                int u = transpose ? edges[i][1] : edges[i][0];
                int v = transpose ? edges[i][0] : edges[i][1];
                to[i] = v;
                next[i] = head[u];
                head[u] = i;
            }
        }
    }

    static Map<String, Object> scc(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        DirectedGraph graph = new DirectedGraph(n, edges, false);
        DirectedGraph transposed = new DirectedGraph(n, edges, true);
        boolean[] seen = new boolean[n];
        int[] finish = new int[n];
        int finished = 0;
        int[] stackV = new int[n];
        int[] stackNext = new int[n];
        for (int start = 0; start < n; start++) {
            if (seen[start]) continue;
            int top = 0;
            stackV[top] = start;
            stackNext[top] = graph.head[start];
            seen[start] = true;
            while (top >= 0) {
                int a = stackNext[top];
                if (a == -1) {
                    finish[finished++] = stackV[top--];
                    continue;
                }
                stackNext[top] = graph.next[a];
                int v = graph.to[a];
                if (!seen[v]) {
                    seen[v] = true;
                    ++top;
                    stackV[top] = v;
                    stackNext[top] = graph.head[v];
                }
            }
        }
        int[] component = new int[n];
        Arrays.fill(component, -1);
        int count = 0;
        for (int i = n - 1; i >= 0; i--) {
            int start = finish[i];
            if (component[start] != -1) continue;
            int top = 0;
            stackV[top] = start;
            component[start] = count;
            while (top >= 0) {
                int u = stackV[top--];
                for (int a = transposed.head[u]; a != -1; a = transposed.next[a]) {
                    int v = transposed.to[a];
                    if (component[v] == -1) {
                        component[v] = count;
                        stackV[++top] = v;
                    }
                }
            }
            count++;
        }
        Set<Long> pairs = new HashSet<>();
        List<int[]> condensation = new ArrayList<>();
        for (int[] e : edges) {
            int a = component[e[0]], b = component[e[1]];
            if (a == b) continue;
            long key = (((long) a) << 32) | (b & 0xffffffffL);
            if (pairs.add(key)) condensation.add(new int[]{a, b});
        }
        return Map.of("component", component, "condensation", condensation);
    }

    /** Returns reversed DFS finish order, or null when any directed cycle exists. */
    private static int[] topologicalOrder(DirectedGraph graph) {
        int n = graph.head.length;
        byte[] state = new byte[n]; // 0 unseen, 1 active, 2 finished.
        int[] stackV = new int[n], stackNext = new int[n], order = new int[n];
        int finished = n;
        for (int start = 0; start < n; start++) {
            if (state[start] != 0) continue;
            int top = 0;
            stackV[top] = start;
            stackNext[top] = graph.head[start];
            state[start] = 1;
            while (top >= 0) {
                int a = stackNext[top];
                if (a == -1) {
                    int v = stackV[top--];
                    state[v] = 2;
                    order[--finished] = v;
                    continue;
                }
                stackNext[top] = graph.next[a];
                int v = graph.to[a];
                if (state[v] == 1) return null;
                if (state[v] == 0) {
                    state[v] = 1;
                    ++top;
                    stackV[top] = v;
                    stackNext[top] = graph.head[v];
                }
            }
        }
        return order;
    }

    static Map<String, Object> topo(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        int[] order = topologicalOrder(new DirectedGraph(n, edges, false));
        if (order == null) return Map.of("acyclic", false, "order", new int[0]);
        return Map.of("acyclic", true, "order", order);
    }

    static Map<String, Object> dag(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int source = Input.source(input);
        int[][] edges = Input.edges(input);
        DirectedGraph graph = new DirectedGraph(n, edges, false);
        int[] order = topologicalOrder(graph); // Check ALL components, even unreachable ones.
        if (order == null) {
            return Map.of("acyclic", false, "distance", new Object[0], "parent_edge", new int[0]);
        }
        Long[] distance = new Long[n]; // null distinguishes unreachable from negative distance.
        int[] parentEdge = new int[n];
        Arrays.fill(parentEdge, -1);
        distance[source] = 0L;
        for (int u : order) {
            if (distance[u] == null) continue;
            for (int a = graph.head[u]; a != -1; a = graph.next[a]) {
                int v = graph.to[a];
                long candidate = distance[u] + (long) edges[a][2];
                if (distance[v] == null || candidate < distance[v]) {
                    distance[v] = candidate;
                    parentEdge[v] = a; // The adjacency slot is the ORIGINAL edge ID.
                }
            }
        }
        return Map.of("acyclic", true, "distance", distance, "parent_edge", parentEdge);
    }

    /**
     * Reconstruct source-to-target vertices from original-ID parent edges.
     * Returns an empty list if target is unreachable. Caller must pass a DAG result.
     * Complexity O(L) time and space for L vertices in the route.
     */
    public static List<Integer> reconstructRoute(int source, int target,
                                                  int[] parentEdge, int[][] edges) {
        List<Integer> backward = new ArrayList<>();
        int v = target;
        while (true) {
            backward.add(v);
            if (v == source) break;
            int id = parentEdge[v];
            if (id < 0) return List.of();
            if (id >= edges.length || edges[id][1] != v || backward.size() > parentEdge.length) {
                throw new IllegalArgumentException("Invalid predecessor witness");
            }
            v = edges[id][0];
        }
        List<Integer> route = new ArrayList<>(backward.size());
        for (int i = backward.size() - 1; i >= 0; i--) route.add(backward.get(i));
        return route;
    }
}
