import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Calls the non-JSON route helper directly, including tied and parallel edges. */
public final class RouteTest {
    private static void eq(List<Integer> actual, List<Integer> expected) {
        if (!actual.equals(expected)) throw new AssertionError(actual + " != " + expected);
    }
    private static int[][] inputEdges(Map<String, Object> request) { return Input.edges(request); }
    private static void checkCost(List<Integer> route, int[] parents, int[][] edges, long total) {
        long sum = 0;
        for (int i = 1; i < route.size(); i++) {
            int child = route.get(i);
            int id = parents[child];
            if (id < 0 || edges[id][0] != route.get(i-1) || edges[id][1] != child) {
                throw new AssertionError("Bad route direction");
            }
            sum += edges[id][2];
        }
        if (sum != total) throw new AssertionError("Cost " + sum + " != " + total);
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Map<String, Object> negative = Map.of("task", "dag", "n", 5, "source", 0,
            "edges", List.of(List.of(0,1,3),List.of(0,2,8),List.of(1,2,-7),List.of(2,3,4)));
        int[][] negEdges = inputEdges(negative);
        int[] negParents = (int[]) Algorithms.dag(negative).get("parent_edge");
        eq(Algorithms.reconstructRoute(0,3,negParents,negEdges), List.of(0,1,2,3));
        eq(Algorithms.reconstructRoute(0,0,negParents,negEdges), List.of(0));
        eq(Algorithms.reconstructRoute(0,4,negParents,negEdges), List.of());
        checkCost(Algorithms.reconstructRoute(0,3,negParents,negEdges), negParents,negEdges,0L);

        Map<String, Object> tied = Map.of("task", "dag", "n", 4, "source", 0,
            "edges", List.of(List.of(0,1,2), List.of(0,1,2), List.of(1,2,-1),
                List.of(0,2,1), List.of(2,3,4)));
        int[][] tiedEdges = inputEdges(tied);
        int[] actualParents = (int[]) Algorithms.dag(tied).get("parent_edge");
        List<Integer> actualRoute = Algorithms.reconstructRoute(0,3,actualParents,tiedEdges);
        checkCost(actualRoute, actualParents,tiedEdges,5L);
        // A second valid witness uses PARALLEL input edge id 1 and direct tied edge id 3.
        int[] alternativeParents = {-1,1,3,4};
        eq(Algorithms.reconstructRoute(0,1,alternativeParents,tiedEdges), List.of(0,1));
        eq(Algorithms.reconstructRoute(0,3,alternativeParents,tiedEdges), List.of(0,2,3));
        checkCost(Algorithms.reconstructRoute(0,3,alternativeParents,tiedEdges),alternativeParents,tiedEdges,5L);
        System.out.println("PASS route helper: reachable [0,1,2,3], source [0], unreachable [], "
             + "tied/parallel alternative [0,2,3]; costs 0 and 5; actual tie route=" + actualRoute);
    }
}
