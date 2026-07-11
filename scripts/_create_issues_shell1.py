#!/usr/bin/env python3
"""Create SHELL-1 engineering issues from JSON files."""
import json
import subprocess
import sys
import os

REPO = "DimonHo/commons-engine"
TOKEN_FILE = os.path.expanduser("~/.config/commons-engine/github-token")

with open(TOKEN_FILE) as f:
    token = f.read().strip()

issue_files = [
    "scripts/_issue_rating.json",
    "scripts/_issue_dispute.json",
    "scripts/_issue_dispatch.json",
    "scripts/_issue_governance.json",
]

created = []
for fpath in issue_files:
    with open(fpath) as f:
        payload = json.load(f)
    data = json.dumps(payload)
    result = subprocess.run(
        ["curl", "-s", "-X", "POST",
         "-H", f"Authorization: token {token}",
         "-H", "Accept: application/vnd.github.v3+json",
         "-H", "Content-Type: application/json",
         "-d", data,
         f"https://api.github.com/repos/{REPO}/issues"],
        capture_output=True, text=True
    )
    resp = json.loads(result.stdout)
    if "number" in resp:
        num = resp["number"]
        title = resp["title"]
        created.append(f"#{num} {title}")
        print(f"Created #{num}: {title}")
    else:
        print(f"ERROR creating {fpath}: {resp.get('message', resp)}", file=sys.stderr)

print("\nCreated issues:")
for c in created:
    print(f"  {c}")
