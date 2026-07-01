#!/usr/bin/env python3
"""拉取 PR/Issue 概览。"""
import json
import subprocess
import sys


def gh(path):
    r = subprocess.run(
        ["bash", "-c", f"source scripts/gh.sh; gh_api GET \"{path}\""],
        capture_output=True, text=True,
    )
    try:
        return json.loads(r.stdout)
    except json.JSONDecodeError:
        return None


pr = gh("/pulls/34")
if pr:
    print("=== PR #34 ===")
    print("  title :", pr.get("title"))
    print("  state :", pr.get("state"))
    print("  mergeable:", pr.get("mergeable"))
    print("  changed_files:", pr.get("changed_files"))
    print("  +add/-del:", pr.get("additions"), "/", pr.get("deletions"))

issues = gh("/issues?state=open&per_page=100")
real = [i for i in (issues or []) if "pull_request" not in i]
from collections import Counter
c = Counter()
for i in real:
    for l in i.get("labels", []):
        c[l["name"]] += 1
print("=== 开放 Issue 数:", len(real), "===")
print("  按模块标签:", {k: v for k, v in sorted(c.items()) if k in
      ["matching", "payment", "rating", "dispute", "governance", "identity", "infra", "docs"]})
print("  按优先级:", {k: v for k, v in sorted(c.items()) if k in ["P0", "P1", "P2", "P3"]})
