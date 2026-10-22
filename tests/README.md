# Own tests — Assignment 3 (Java 17)

All test inputs are JSON Lines using the official template contract. The matching `expected.jsonl` contains manually justified **reference** outputs and written reasons, while `actual_outputs.jsonl` contains results of a real run. For nonunique answers, use semantic validity checks; never require identical ID order, SCC labels or tie choices.

From the repository root:

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

- `verify_custom.py`: 36 cases, plus alternative valid and invalid witnesses, plus 36 mixed requests in one process. It writes `actual_outputs.jsonl`.
- `RouteTest.java`: directly checks the route helper, including original input edge IDs.
- `stress_limits.py`: deterministic full-limit 100,000 vertex / 250,000 edge-or-operation inputs, independently checked for six tasks (without checking in multi-megabyte generated inputs). It writes `stress_results.txt`.
- `Benchmark.java`: three sizes, four warm-ups and seven real nanosecond timings per method and size on identical graphs; writes CSV and medians.

For algorithm steps, correctness proofs, benchmarking environment and outputs, see `../ANALYSIS.md`.
