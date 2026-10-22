import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Reproducible identical-input Java benchmark. Run from repository root. */
public final class Benchmark {
    private static final long SEED = 20261023L;
    private static final int WARMUPS = 4;
    private static final int TRIALS = 7;

    private static Map<String,Object> graph(int n, int m) {
        Random rng = new Random(SEED + n*31L + m);
        List<List<Integer>> edges = new ArrayList<>(m);
        // Connected base chain, then random loops/parallel edges, including negative weights.
        for (int i=0; i<n-1; i++) edges.add(List.of(i,i+1,rng.nextInt(2001)-1000));
        for (int i=n-1; i<m; i++) edges.add(List.of(rng.nextInt(n), rng.nextInt(n),rng.nextInt(2001)-1000));
        return Map.of("n",n,"edges",edges);
    }

    private static void validate(Map<String,Object> request, Map<String,Object> result) {
        int n = Input.vertexCount(request);
        int[][] edges = Input.edges(request);
        int components=((Number) result.get("components")).intValue();
        long cost=((Number) result.get("cost")).longValue();
        @SuppressWarnings("unchecked") List<Integer> chosen=(List<Integer>) result.get("edge_ids");
        if (components!=1 || chosen.size()!=n-1) throw new AssertionError("wrong count");
        int[] p=new int[n],sz=new int[n];
        for (int i=0;i<n;i++){p[i]=i;sz[i]=1;}
        boolean[] used=new boolean[edges.length];
        long sum=0;
        for (int id:chosen) {
            if (id<0||id>=edges.length||used[id]) throw new AssertionError("repeated/out-of-range edge ID");
            used[id]=true;
            int a=root(p,edges[id][0]),b=root(p,edges[id][1]);
            if(a==b)throw new AssertionError("cycle in witness");
            if(sz[a]<sz[b]){int tmp=a;a=b;b=tmp;}
            p[b]=a;sz[a]+=sz[b];sum+=edges[id][2];
        }
        if (sum!=cost) throw new AssertionError("cost differs from sum");
    }
    private static int root(int[] p,int u){while(p[u]!=u){p[u]=p[p[u]];u=p[u];}return u;}
    private static Map<String,Object> method(boolean kruskal, Map<String,Object> graph) {
        return kruskal ? Algorithms.kruskal(graph) : Algorithms.prim(graph);
    }
    private static long median(long[] values) {
        long[] copy=values.clone();Arrays.sort(copy);return copy[copy.length/2];
    }
    public static void main(String[] args) {
        System.out.println("n,m,method,trial,elapsed_ns");
        int[][] sizes={{1000,4000},{5000,20000},{20000,80000}};
        for (int[] size:sizes) {
            int n=size[0],m=size[1];
            Map<String,Object> request=graph(n,m);
            // Entire graph is pre-generated once and passed to both implementations.
            long expectedCost=((Number) Algorithms.kruskal(request).get("cost")).longValue();
            for (int j=0;j<WARMUPS;j++) {
                for (boolean kruskal:new boolean[]{true,false}) {
                    Map<String,Object> a=method(kruskal,request);
                    validate(request,a);
                    if(((Number)a.get("cost")).longValue()!=expectedCost)throw new AssertionError("unequal MST costs");
                }
            }
            long[] kTimes=new long[TRIALS],pTimes=new long[TRIALS];
            for (int j=0;j<TRIALS;j++) {
                // Alternate run order to reduce systematic first-method advantage.
                boolean[] order=j%2==0?new boolean[]{true,false}:new boolean[]{false,true};
                for (boolean isKruskal:order) {
                    long start=System.nanoTime();
                    Map<String,Object> result=method(isKruskal,request);
                    long elapsed=System.nanoTime()-start;
                    validate(request,result); // OUTSIDE the timing boundary.
                    if (((Number)result.get("cost")).longValue()!=expectedCost)
                        throw new AssertionError("Cost mismatch");
                    (isKruskal?kTimes:pTimes)[j]=elapsed;
                    System.out.printf("%d,%d,%s,%d,%d%n",n,m,isKruskal?"Kruskal":"Prim",j+1,elapsed);
                }
            }
            System.err.printf("Medians n=%d m=%d: Kruskal=%d ns Prim=%d ns. Both forests verified.%n",
                    n,m,median(kTimes),median(pTimes));
        }
    }
}
