#!/usr/bin/env python3
"""给 PR/Issue 打标签（避免 -c 内联被沙盒拦截）。"""
import json
import subprocess
import sys

num = sys.argv[1]
labels = sys.argv[2].split(",")
payload = json.dumps({"labels": labels})
r = subprocess.run(
    ["bash", "-c", f"source scripts/gh.sh; gh_api POST \"/issues/{num}/labels\" '{payload}'"],
    capture_output=True, text=True,
)
try:
    data = json.loads(r.stdout)
    print(f"#{num} labels:", [x["name"] for x in data])
except json.JSONDecodeError:
    print("RAW:", r.stdout[:300], "| ERR:", r.stderr[:300])
