#!/usr/bin/env python3
import json, subprocess

def call(fn):
    return subprocess.run(['bash', '-c', f'source scripts/gh.sh; {fn}'],
                          capture_output=True, text=True).stdout

issues_raw = call('gh_api GET "/issues?state=all&per_page=100"')
try:
    issues = json.loads(issues_raw)
except json.JSONDecodeError:
    issues = []
prs = [i for i in issues if 'pull_request' in i]
real = [i for i in issues if 'pull_request' not in i]
print(f"All items: {len(issues)} | Issues: {len(real)} | PRs: {len(prs)}")
for i in real[:10]:
    print(f" ISSUE #{i['number']} {i['title']}")

labels_raw = call('gh_api GET "/labels"')
try:
    labels = json.loads(labels_raw)
    print("LABELS:", [l['name'] for l in labels])
except json.JSONDecodeError:
    print("LABELS raw:", labels_raw[:200])
