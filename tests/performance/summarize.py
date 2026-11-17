#!/usr/bin/env python3
import csv,json,pathlib,statistics,sys
root=pathlib.Path(sys.argv[1]);summary=[]
for version in ['before','current']:
 lines=(root/version/'results.csv').read_text().splitlines();rows=list(csv.DictReader(lines[1:]))
 for kind,n,r in sorted(set((x['kind'],x['images'],x['roots']) for x in rows)):
  group=[x for x in rows if (x['kind'],x['images'],x['roots'])==(kind,n,r)]
  summary.append(dict(version=version,kind=kind,images=int(n),roots=int(r),median_ms=statistics.median(float(x['elapsed_ms']) for x in group),fs_probe_calls=int(group[0]['fs_probe_calls']),original_path_calls=int(group[0]['original_path_calls'])))
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))
