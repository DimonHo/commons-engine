#!/usr/bin/env python3
"""Create a GitHub PR via API."""
import json, os, sys, urllib.request

TOKEN = open(os.path.expanduser("~/.config/commons-engine/github-token")).read().strip()
REPO = "DimonHo/commons-engine"

def create_pr(title, head, base, body):
    url = f"https://api.github.com/repos/{REPO}/pulls"
    data = json.dumps({"title": title, "head": head, "base": base, "body": body}).encode()
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Authorization", f"token {TOKEN}")
    req.add_header("Content-Type", "application/json")
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())

if __name__ == "__main__":
    title = sys.argv[1]
    head = sys.argv[2]
    body_file = sys.argv[3]
    with open(body_file) as f:
        body = f.read()
    result = create_pr(title, head, "main", body)
    print(f"PR #{result['number']} created: {result['html_url']}")
