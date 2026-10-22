"""Deterministic full-limit inputs; independent checks. Usage: python tests/stress_limits.py"""
import json
import pathlib
import random
import subprocess
import time

P=pathlib.Path(__file__).resolve().parents[1]
N=100_000
M=250_000
SEED=20261023

def execute(request):
    payload=json.dumps(request,separators=(',',':'))+'\n'
    start=time.perf_counter()
    result=subprocess.run(['java','-Xmx384m','-cp',str(P/'.build'/'Java'),'Main'],input=payload,
        capture_output=True,text=True,encoding='utf-8',timeout=90,check=True)
    duration=time.perf_counter()-start
    lines=result.stdout.splitlines()
    assert len(lines)==1, (len(lines),result.stderr[:200])
    return json.loads(lines[0]), duration

def find(parent, v):
    while parent[v]!=v:
        parent[v]=parent[parent[v]]
        v=parent[v]
    return v

def independent_mst(edges):
    parent=list(range(N)); sizes=[1]*N; total=0; chosen=0
    for u,v,w in sorted(edges,key=lambda e:e[2]):
        a,b=find(parent,u),find(parent,v)
        if a!=b:
            if sizes[a]<sizes[b]: a,b=b,a
            parent[b]=a; sizes[a]+=sizes[b]; total+=w;chosen+=1
    assert chosen==N-1
    return total

def valid_forest(edges, answer, expected_cost):
    assert answer['components']==1 and answer['cost']==expected_cost
    ids=answer['edge_ids']; assert len(ids)==N-1 and len(set(ids))==N-1
    parent=list(range(N)); sizes=[1]*N; total=0
    for i in ids:
        u,v,w=edges[i]; a,b=find(parent,u),find(parent,v)
        assert a!=b
        if sizes[a]<sizes[b]: a,b=b,a
        parent[b]=a; sizes[a]+=sizes[b]; total+=w
    assert total==expected_cost

def main():
    results=[]
    # Maximum DSU operation count, deep connectivity, repeated unions and loops.
    ops=[[i,i+1] for i in range(N-1)] + [[0,0],[N-1,N-1]]
    rng=random.Random(SEED)
    ops.extend([[rng.randrange(N), rng.randrange(N)] for _ in range(M-len(ops))])
    ans,t=execute({'task':'dsu','n':N,'operations':ops})
    assert len(ans['states'])==M and ans['states'][N-2]==[1,N] and ans['states'][-1]==[1,N]
    results.append(('DSU',N,M,round(t,3),'PASS: all vertices connected; q=250,000'))

    rng=random.Random(SEED+1)
    weighted=[[i,i+1,(i%19)-9] for i in range(N-1)]
    while len(weighted)<M:
        weighted.append([rng.randrange(N),rng.randrange(N),rng.randint(-1_000_000_000,1_000_000_000)])
    reference=independent_mst(weighted)
    for task in ('kruskal','prim'):
        ans,t=execute({'task':task,'n':N,'edges':weighted})
        valid_forest(weighted,ans,reference)
        results.append((task,N,M,round(t,3),f'PASS: independent MST cost {reference}; valid witness'))

    # Full-limit deep chain + only FORWARD extra arcs; every vertex is an SCC on its own.
    rng=random.Random(SEED+2)
    directed=[[i,i+1] for i in range(N-1)]
    while len(directed)<M:
        u=rng.randrange(N-1)
        v=rng.randrange(u+1,N)
        directed.append([u,v])
    ans,t=execute({'task':'scc','n':N,'edges':directed})
    labels=ans['component']; pairs=ans['condensation']
    assert len(labels)==N and len(set(labels))==N
    expected_pairs={(labels[u],labels[v]) for u,v in directed}
    assert {tuple(p) for p in pairs}==expected_pairs and len(pairs)==len(expected_pairs)
    results.append(('scc',N,M,round(t,3),'PASS: 100,000 singleton SCCs; exact condensation'))
    ans,t=execute({'task':'topo','n':N,'edges':directed})
    assert ans['acyclic'] is True and len(ans['order'])==N
    positions=[0]*N
    for i,u in enumerate(ans['order']):positions[u]=i
    assert all(positions[u]<positions[v] for u,v in directed)
    results.append(('topo',N,M,round(t,3),'PASS: valid order on a 100,000-deep DAG'))
    dag_edges=[[u,v,((j*11939)%2001)-1000] for j,(u,v) in enumerate(directed)]
    adj=[[] for _ in range(N)]
    for i,(u,v,w) in enumerate(dag_edges):adj[u].append((v,w,i))
    ref=[None]*N;ref[0]=0
    for u in range(N):
        if ref[u] is None:continue
        for v,w,i in adj[u]:
            alt=ref[u]+w
            if ref[v] is None or alt<ref[v]:ref[v]=alt
    ans,t=execute({'task':'dag','n':N,'source':0,'edges':dag_edges})
    assert ans['acyclic'] is True and ans['distance']==ref
    parents=ans['parent_edge'];assert len(parents)==N and parents[0]==-1
    for v in range(1,N):
        id=parents[v]; assert 0<=id<M
        u,to,w=dag_edges[id];assert to==v and ref[u]+w==ref[v]
    results.append(('dag',N,M,round(t,3),'PASS: distances match independent forward-order DP; valid parents'))
    output='\n'.join(f'{name}: n={n}, operations/edges={m}, full process wall time={seconds}s, {status}'
                     for name,n,m,seconds,status in results)+'\n'
    (P/'tests'/'stress_results.txt').write_text('Seed: 20261023 (forests use +1; directed graph uses +2).\n'
        'Environment: this evaluation container; Python time.perf_counter includes Java process startup, JSON parse and serialization.\n'+output,encoding='utf-8')
    print(output)

if __name__=='__main__':main()
