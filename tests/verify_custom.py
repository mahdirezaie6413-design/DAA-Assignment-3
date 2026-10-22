"""Run custom fixed cases, compare semantic witnesses, and test checker strictness.

Usage from repository root: python tests/verify_custom.py
Writes tests/actual_outputs.jsonl using the real Java adapter output.
"""
import importlib.util
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
T = ROOT / 'tests'
spec = importlib.util.spec_from_file_location('public_validate', ROOT/'tools'/'public_validate.py')
validate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validate)

def load_lines(path):
    return [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines() if line.strip()]

def main():
    requests = load_lines(T/'inputs.jsonl')
    references = load_lines(T/'expected.jsonl')
    assert len(requests) == len(references)
    cmd = ['python', 'tools/run.py', '--input', 'tests/inputs.jsonl']
    run = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, encoding='utf-8', timeout=80)
    if run.returncode:
        sys.exit('Runner failed: '+run.stderr)
    answers = [json.loads(line) for line in run.stdout.splitlines()]
    assert len(answers) == len(requests), (len(answers), len(requests))
    (T/'actual_outputs.jsonl').write_text(''.join(json.dumps({'name':ref['name'],'actual':ans},ensure_ascii=False,separators=(',',':'))+'\n' for ref,ans in zip(references,answers)), encoding='utf-8')
    for request, reference, actual in zip(requests, references, answers):
        assert validate.check(request, actual, reference['expected']), ('INVALID',reference['name'],actual)
    named = {entry['name']:(req,entry['expected'],actual) for req,entry,actual in zip(requests,references,answers)}

    # Validate intentionally DIFFERENT correct witnesses, and intentionally BAD witnesses.
    req,e,a = named['kruskal-equal-weight-triangle']
    assert validate.check(req, {'cost':2,'components':1,'edge_ids':[1,2]}, e)
    assert not validate.check(req, {'cost':2,'components':1,'edge_ids':[0,0]}, e)
    req,e,a = named['prim-equal-weight-triangle']
    assert validate.check(req, {'cost':2,'components':1,'edge_ids':[0,2]}, e)
    assert not validate.check(req, {'cost':2,'components':1,'edge_ids':[0,0]}, e)
    req,e,a = named['scc-two-cycles-chain']
    alt_labels = [a['component'][v]+100 for v in range(req['n'])]
    alt_pairs = [[u+100,v+100] for u,v in a['condensation']]
    assert validate.check(req, {'component':alt_labels,'condensation':alt_pairs}, e)
    assert not validate.check(req, {'component':a['component'],'condensation':a['condensation']+[a['condensation'][0]]},e)
    req,e,a = named['topo-precedence']
    assert validate.check(req, {'acyclic':True,'order':[0,2,1,3]}, e)
    assert not validate.check(req, {'acyclic':True,'order':[3,2,1,0]},e)
    req,e,a = named['dag-parallel-tied']
    assert validate.check(req, {'acyclic':True,'distance':[0,2,1,5],'parent_edge':[-1,1,3,4]},e)
    assert not validate.check(req, {'acyclic':True,'distance':[0,2,1,5],'parent_edge':[-1,1,2,1]},e)

    # A second, repeated mixed workload in the SAME Java process verifies request isolation.
    mixed_indices = [2,5,16,19,24,26,28,35,0,13,22,29]
    mixed = [requests[i] for i in mixed_indices] * 3
    proc = subprocess.run(['java','-Xmx384m','-cp',str(ROOT/'.build'/'Java'),'Main'],
        cwd=ROOT,input=''.join(json.dumps(row)+'\n' for row in mixed),
        capture_output=True,text=True,encoding='utf-8',timeout=25,check=True)
    mix_answers = [json.loads(x) for x in proc.stdout.splitlines()]
    assert len(mix_answers) == len(mixed)
    for j,answer in enumerate(mix_answers):
        idx = mixed_indices[j % len(mixed_indices)]
        assert validate.check(requests[idx], answer, references[idx]['expected'])
    print(f'PASS: {len(requests)} independent fixed cases, alternative valid and invalid witnesses, '
          f'and {len(mixed)} repeated mixed requests. Actual outputs saved.')

if __name__ == '__main__':
    main()
