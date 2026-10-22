# Assignment 3 — Graph Algorithms: Design, Correctness, Tests and Benchmark

**Course:** Design and Analysis of Algorithms  
**Selected language:** Java 17 (`submission.json` contains `{"language":"Java"}`)  
**Source:** `src/Java/Algorithms.java`  
**Template:** Assignment 3 campus festival graph-algorithms repository, retaining the supplied `Input.java`, `Json.java`, `Main.java` and `tools/` interface.

## How to build, run and verify

Run these commands from the repository root. `python` may be `py -3` on Windows. Use an installed JDK 17 or newer and Python 3.12 or newer.

```sh
python tools/run.py --setup
python tools/run.py --check
python tools/run.py --input tests/inputs.jsonl
python tests/verify_custom.py
javac --release 17 -cp .build/Java -d .build/Java tests/RouteTest.java
java -cp .build/Java RouteTest
python tests/stress_limits.py
javac --release 17 -cp .build/Java -d .build/Java tests/Benchmark.java
java -Xmx384m -cp .build/Java Benchmark > tests/benchmark_results.csv 2> tests/benchmark_medians.txt
```

The `tools/run.py` launcher itself compiles `src/Java/*.java` using `javac --release 17`; no external dependencies or downloads are required. The first command verifies `ping`; the second runs 36 public cases followed by two batches in one persistent process. The custom test program invokes the same real JSON Lines interface, tests semantic witnesses and records **actual** outputs in `tests/actual_outputs.jsonl`. Run the launcher before either standalone Java test so `.build/Java` contains the freshly compiled classes.

## 1. Disjoint-set union (`dsu`)

**Algorithm.** Create one parent and size per vertex. `find(x)` follows parent links, compressing them with path halving. `union(u,v)` finds both roots, attaches the smaller tree below the larger one, and decreases `components` only when the roots differ. Update `largest` from the merged size. Append the state **after** every operation, including repetitions.

**Invariant.** Parent-root equivalence exactly matches connectivity under all processed unions; root sizes are exact component cardinalities. `components` counts roots, and `largest` is the maximum root size. It holds before any union. A same-root union changes nothing; a different-root union merges exactly two components and updates counts and sizes, preserving the invariant. Therefore every reported `[components,largest]` is correct.

**Boundaries.** `n=0` yields `states=[]`; with positive `n`, initial largest is one. Self-unions, repeats and joining already-connected vertices do not change a state. No recursion is required. **Time:** `O(n + q α(n))` amortized. **Memory:** `O(n+q)` including output states, `O(n)` auxiliary.

## 2. Kruskal (`kruskal`)

**Algorithm.** Retain original IDs `0..m-1`, sort by `(weight, ID)`, and scan in ascending order. Add edge ID if and only if its endpoints are in different DSU components; sum chosen weights in a Java `long`. Return remaining DSU count, which includes isolated vertices.

**Invariant and proof.** Chosen edges always form a forest because an edge is accepted only between different components. At each accepted step, the lightest eligible edge crossing a cut between current DSU components is safe by the MST cut property. This maintains an extension to some minimum spanning forest (MSF). After all edges, each original connected component has been spanned, so the forest is minimum-cost. Self-loops are rejected automatically, while negative, parallel and tied edges are handled by sorting and root checks.

**Boundaries.** Empty graph returns `(cost=0, edge_ids=[], components=0)`. Isolates are counted, loops are skipped, and weights `±10^9` can accumulate beyond 32-bit range. **Time:** `O(n + m log(m+1) + m α(n))`, commonly `O(n+m log(m+1))`. **Memory:** `O(n+m)` including sorted IDs and witness.

## 3. Prim (`prim`)

**Algorithm.** Build an undirected adjacency structure with original input edge IDs. For every unvisited start vertex, mark it, increment `components`, and enqueue incident edges to unvisited vertices into a min-heap `(weight, ID)`. Repeatedly remove the cheapest candidate; skip it if its destination is visited, otherwise add its original edge ID and weight, mark its destination, and push its outgoing crossing candidates. After a component's heap empties, restart from the next unvisited vertex.

**Invariant and proof.** For an active component, `visited` forms a growing tree. Heap candidates include every crossing edge from the current tree to unvisited vertices (and may include stale edges). Removing stale candidates cannot change the selected minimum crossing weight. The first non-stale popped edge is safe by the cut property and extends the tree without cycles. Repeating reaches every vertex connected to that component; restarts handle all components and isolates. Summing these trees yields a minimum spanning forest.

**Boundaries.** Undirected self-loops are never eligible because their destination is already visited. Parallel/equal and negative edges are allowed. The heap is drained before processing the next component. `cost` is `long`. **Time:** `O(n + m log(m+1))` with a lazy heap. **Memory:** `O(n+m)` for adjacency, heap, visited array and edge witness.

## 4. Kosaraju strongly connected components (`scc`)

**Algorithm.** Construct adjacency arrays for the original graph and its transpose. First, perform full-graph DFS and record vertices in finishing order. Second, process vertices in **decreasing** finishing order, running DFS on the transpose to label every vertex in each SCC. Finally, map each input edge from its source label to its destination label, discarding internal edges and deduplicating cross-component pairs with a hash set of packed 64-bit label pairs.

**Invariant and proof.** The first DFS records true DFS finishing times. The standard Kosaraju ordering property ensures that a transpose DFS started from the greatest unassigned finish time cannot leave that SCC into a different, as-yet-unassigned SCC. Inside an SCC, every vertex can reach all others in either graph; thus the second-pass DFS discovers exactly one SCC. Repeating partitions all vertices into maximal strongly connected sets. The final pass considers all original directed edges and inserts each distinct cross-SCC pair exactly once, so condensation is exact.

**Boundaries.** `n=0` gives empty arrays, isolated vertices yield singleton SCCs, self-loops stay internal and duplicate inter-SCC edges are deduplicated. **Time:** `O(n+m)` expected with hash-set operations. **Memory:** `O(n+m)` including transpose, both stacks, finishing order and condensation.

## 5. Three-state DFS topological order (`topo`)

**Algorithm.** DFS each as-yet-unvisited vertex using state `0=unseen`, `1=active`, `2=finished`. Retain `(vertex,next-adjacency-position)` in two **explicit stacks**, so each edge is scanned once. If any edge reaches state 1, return `acyclic=false` and an empty order. Otherwise, put each vertex into output from right to left when it finishes.

**Invariant and proof.** Active vertices are exactly the current DFS path. A directed edge to an active vertex closes a directed cycle; every DFS-detectable directed cycle has a back edge to an active vertex. For a DAG, when DFS finishes a vertex, all descendants reached from it have finished. Therefore its position before those descendants in reversed finish order respects every input edge; the full result is a valid topological order.

**Boundaries.** Self-loops and cycles outside the first traversal are detected, while edges to finished vertices are harmless. Parallel edges have no effect on correctness. `n=0` returns `true,[]`. **Time:** `O(n+m)`. **Memory:** `O(n+m)` including adjacency, stack and order; only `O(n)` additional working state beyond adjacency.

## 6. DAG shortest paths (`dag`) and route reconstruction

**Algorithm.** First run the same **whole-graph** DFS cycle detector; any cycle anywhere returns `acyclic=false` and two empty arrays. Otherwise initialize `distance[source]=0L`, all others to `null`, and every parent edge to `-1`. Visit vertices in topological order. Only from a reachable vertex, relax each outgoing original edge `(u,v,w)` if `distance[u]+(long)w` improves `distance[v]`. Store the **original edge ID** as `parent_edge[v]` whenever an improvement is made.

**Invariant and proof.** Before processing a vertex in topological order, all its possible DAG predecessors have already been processed. Its current finite distance is therefore the shortest possible source-to-vertex distance: every source-to-vertex path ends in one of those incoming edges. Its `parent_edge` records an edge attaining that value. Relaxing from a vertex preserves the optimal candidate for later vertices; after all vertices, distances are optimal and parent edges describe paths back to the source. Acyclicity prevents predecessor cycles. Unreachable vertices are never relaxed and remain `null` with parent `-1`.

**Boundaries.** A cycle unreachable from the source still invalidates the graph. Negative weights and distances, including exactly `-1`, are valid and are not confused with unreachable `null`. Long arithmetic covers large sums. Ties retain any already-valid optimal predecessor. The source has distance zero and parent `-1`. **Time:** `O(n+m)`. **Memory:** `O(n+m)` including adjacency and output.

**Route helper.** `Algorithms.reconstructRoute(source,target,parentEdge,edges)` follows each original incoming edge ID backward from target until source, then reverses the vertex sequence. If it meets `-1` before the source it returns `[]`. Every step follows the edge's destination-to-source backwards, so reversal reconstructs correct edge direction. It rejects malformed predecessor references; normal DAG parent arrays are well-formed. With a route of `L` vertices, **time and output/working space** are `O(L)`.

Direct test calls in `tests/RouteTest.java` were **executed** with the following results:

| Case | Helper call (arguments abbreviated) | Observed route | Verified direction and cost |
|---|---|---|---|
| Reachable | `reconstructRoute(0, 3, [-1,0,2,3,-1], sampleEdges)` | `[0,1,2,3]` | `0→1→2→3`; edge IDs `0,2,3`; `3-7+4=0` |
| Source | `reconstructRoute(0, 0, [-1,0,2,3,-1], sampleEdges)` | `[0]` | No edges; cost `0` |
| Unreachable | `reconstructRoute(0, 4, [-1,0,2,3,-1], sampleEdges)` | `[]` | No source-to-target route |
| Parallel tie | `reconstructRoute(0, 1, [-1,1,3,4], tiedEdges)` | `[0,1]` | Original edge ID `1`, weight `2` (parallel to ID `0`) |
| Alternative tied shortest path | `reconstructRoute(0, 3, [-1,1,3,4], tiedEdges)` | `[0,2,3]` | Original edge IDs `3,4`; `1+4=5` |

For tied/parallel edges, the real algorithm also chose the valid parent IDs `[-1,1,3,4]` in the fixed test. Different valid tie choices are permitted. The fixed shortest-path results contain `distance=[0,2,1,5]`; the alternative path through vertex 1 (`edge IDs [1,2,4]`) also costs `2-1+4=5`.

## State reset, deep graphs and interface

`Main.java`, `Input.java`, and `Json.java` are retained from the template. The application reads UTF-8 JSON Lines, writes exactly one JSON result per input line, keeps `ping`, and writes no diagnostics to standard output. All algorithm arrays, DSU parents, temporary stacks, heaps and sets are allocated within the method call. No mutable state persists between requests. The public launcher repeats all 36 cases twice within one process; the independent custom suite runs 36 further mixed requests in one process. Both passed.

No implementation uses recursive graph DFS. The DFS stacks store both vertices and the next unprocessed outgoing edge; this avoids Java stack overflow on a 100,000-vertex chain and avoids rescanning edges. Union-find root lookup is iterative. Memory includes all returned arrays and original input IDs; no 32-bit arithmetic is used for costs or distances.

## Own tests and independent correctness checks

- `tests/inputs.jsonl`: **36** independently selected cases spanning ping, zero/one vertices, self and repeated unions, disconnected forests, loops, parallel/tied edges, negative values, 64-bit totals, SCC condensation, cycles, unreachable vertices and nonzero DAG sources.
- `tests/expected.jsonl`: a matching name, manually justified expected result/reference and reason for each case. In tasks with nonunique labels/forests/orders/parents, these values are **references**, not exact-output demands.
- `tests/actual_outputs.jsonl`: **36 actual results** recorded from the Java JSON Lines program.
- `tests/verify_custom.py`: compares answers using the template's **semantic validator** (`tools/public_validate.py`), accepts alternative valid optimum forests, SCC relabeling, a different topological order and tied shortest-path parents; rejects a cyclic/duplicate-edge forest witness, a duplicate condensation pair, backwards topological order, and a wrong DAG predecessor. It also runs **36 mixed requests** in one Java process to verify isolation.
- `tests/RouteTest.java`: invokes route reconstruction directly for reachable, source, unreachable, tied and parallel cases; checks direction and exact sum of original-edge weights.
- `tests/stress_limits.py`: deterministic generated tests at maximum `n=100000` and `m/q=250000`, including a **100,000-deep chain**. DSU verifies end states; Kruskal and Prim are checked against a separately written Python Kruskal reference and individually validated for forest feasibility and cost. For the strictly forward directed graph, SCC checks singleton labels and exact condensation, topo checks every edge precedence, and DAG distances are checked against an independently coded vertex-order dynamic program plus incoming-edge witnesses. Results are in `tests/stress_results.txt`.

**Executed local verification results:** `PASS setup: ping`; all **36 public cases PASS**; `PASS state reset: two complete batches in one process`; 36/36 own fixed cases pass; alternative-valid and invalid-witness tests pass; 36 repeated mixed requests pass; route tests pass; and all six maximum-limit tests pass. These are local runs against the uploaded public template, **not** a GitHub Actions or Moodle grading result. Public checks report pass/fail, not a score.

## Kruskal–Prim benchmark: same graphs and actual measured timings

**Controlled inputs.** `tests/Benchmark.java` generates ONE weighted, connected, undirected graph per size, reused unchanged by both methods. The first `n−1` edges form a path; the remaining edges use `java.util.Random(seed = 20261023 + 31*n + m)` with random endpoints (loops/parallel edges possible), and all weights are uniformly generated with `nextInt(2001)-1000`. Sizes are `(n,m)=(1000,4000), (5000,20000), (20000,80000)`. Prior to recording timings, a valid forest and equal optimal cost are checked for both methods. Each measured result is **also** verified for `n-1` non-repeating cycle-free selected edge IDs, the summed cost and equal optimum.

**Environment.** A Linux 6.18.55 x86-64 container reporting **AMD EPYC 9V74 80-Core Processor**, with 5 logical CPUs exposed; OpenJDK **21.0.12.1** compiling project code using `--release 17` (Java 17 bytecode/API compatibility), JVM `-Xmx384m`. Python 3.13.5 was used for test scripts. Local container timings can vary with shared machine load; the user should rerun locally for their own hardware if required.

**Timing protocol.** Timer: Java `System.nanoTime()`. Compile Java before benchmarking, outside timing. Each graph is built once **outside** timing and given to both algorithms as the exact same request object; no JSON text parsing, subprocess launching, result serialization, graph generation, or witness validation is counted. **Included inside** timing are `Algorithms.kruskal(request)` or `Algorithms.prim(request)` from entry through returned `Map`, including the supplied `Input.edges()` conversion, algorithm-specific adjacency/sorting/heap construction, DSU/traversal and output creation. There are **4 warm-up calls per method and size** and **7 independent timed calls per method and size**. Call order alternates between methods. The median is the middle of the seven sorted times. Values below are milliseconds, rounded only for display; raw nanoseconds are preserved in `tests/benchmark_results.csv`.

| Graph size | Method | Trial 1 | Trial 2 | Trial 3 | Trial 4 | Trial 5 | Trial 6 | Trial 7 | Median |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| n=1,000, m=4,000 | Kruskal | 2.219 | 1.910 | 1.909 | 1.801 | 1.737 | 1.630 | 1.526 | **1.801** |
| n=1,000, m=4,000 | Prim | 1.986 | 1.293 | 2.011 | 2.099 | 1.610 | 1.634 | 1.486 | **1.634** |
| n=5,000, m=20,000 | Kruskal | 18.384 | 14.734 | 21.074 | 17.604 | 20.168 | 11.099 | 11.205 | **17.604** |
| n=5,000, m=20,000 | Prim | 9.977 | 10.155 | 9.906 | 7.794 | 8.344 | 7.312 | 7.478 | **8.344** |
| n=20,000, m=80,000 | Kruskal | 24.181 | 20.235 | 21.432 | 22.243 | 36.564 | 22.453 | 23.718 | **22.453** |
| n=20,000, m=80,000 | Prim | 40.608 | 41.244 | 35.787 | 36.295 | 43.065 | 40.865 | 42.995 | **40.865** |

**Commands to reproduce measured runs:**

```sh
python tools/run.py --setup
javac --release 17 -cp .build/Java -d .build/Java tests/Benchmark.java
java -Xmx384m -cp .build/Java Benchmark > tests/benchmark_results.csv 2> tests/benchmark_medians.txt
```

**Observations vs complexity.** Both algorithms met their respective `O(n+m log m)` upper bounds and returned checked optimal spanning forests. Prim's median was slightly lower at the smallest size, substantially lower in the middle, and higher in the largest tested size. This is plausible because Kruskal's object-array sort and Prim's lazy heap/adjacency allocations have different constants and JVM/GC profiles. One container run with seven trials per size does **not** establish a universal speed ranking. The benchmark deliberately tests equal graphs but does not isolate algorithm-only work from supplied input-array conversion; it holds this boundary consistent for both methods. Hardware variability, JIT activity, GC, random graph degree distribution, and graph density may change timings.

## Sources and assistance / academic integrity

- Assignment requirements, JSON contract, validator, launcher and starter code: uploaded `daa-assignment-3-graphs-main` template (`README.md`, `tools/`, `src/Java/`).
- Standard algorithmic reasoning: textbook-level DSU union-by-size/path compression, MST cut property, Kosaraju two-pass DFS, DFS finish order and DAG topological relaxation. No third-party algorithm implementation or downloaded library was copied into the solution.
- **AI disclosure:** OpenAI ChatGPT (GPT-6) assisted with generating and explaining algorithm implementations, writing test cases and report text, and planning the benchmark. The Java sources, test scripts and recorded results were executed and checked in a local evaluation container. The submitting student must review the code, ensure independent understanding, comply with the course syllabus regarding AI assistance, and only represent verification they have personally performed when speaking to the instructor.
