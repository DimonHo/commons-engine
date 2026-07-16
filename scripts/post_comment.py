#!/usr/bin/env python3
"""Post a comment to a GitHub issue/PR via API."""
import json, os, sys, urllib.request

TOKEN = open(os.path.expanduser("~/.config/commons-engine/github-token")).read().strip()
REPO = "DimonHo/commons-engine"

def post_comment(issue_num, body):
    url = f"https://api.github.com/repos/{REPO}/issues/{issue_num}/comments"
    data = json.dumps({"body": body}).encode()
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Authorization", f"token {TOKEN}")
    req.add_header("Content-Type", "application/json")
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())

if __name__ == "__main__":
    issue_num = int(sys.argv[1])
    body_file = sys.argv[2]
    with open(body_file) as f:
        body = f.read()
    result = post_comment(issue_num, body)
    print(f"Comment posted to #{issue_num}: {result['html_url']}")
