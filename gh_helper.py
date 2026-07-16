#!/usr/bin/env python3
"""GitHub API helper - replaces jq dependency for gh.sh functions."""
import sys
import json
import urllib.request
import urllib.error
import os

REPO = "DimonHo/commons-engine"
TOKEN_FILE = os.environ.get("COMMONS_ENGINE_TOKEN_FILE",
    os.path.expanduser("~/.config/commons-engine/github-token"))


def get_token():
    if os.path.isfile(TOKEN_FILE):
        with open(TOKEN_FILE) as f:
            return f.read().strip()
    return os.environ.get("GITHUB_TOKEN", "")


def api(method, endpoint, data=None):
    token = get_token()
    if not token:
        print("ERROR: no token", file=sys.stderr)
        sys.exit(1)
    url = f"https://api.github.com/repos/{REPO}{endpoint}"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
    }
    body = None
    if data is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return {"error": e.code, "message": e.read().decode()[:500]}


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "pr_diff":
        pr_num = sys.argv[2]
        pr = api("GET", f"/pulls/{pr_num}")
        diff_url = pr.get("diff_url", "")
        if diff_url:
            req = urllib.request.Request(diff_url)
            with urllib.request.urlopen(req) as resp:
                print(resp.read().decode())
    elif cmd == "review_pr":
        pr_num = sys.argv[2]
        event = sys.argv[3]
        body = sys.argv[4]
        result = api("POST", f"/pulls/{pr_num}/reviews",
                      {"event": event, "body": body})
        print(json.dumps(result, indent=2)[:500])
    elif cmd == "label_issue":
        issue_num = sys.argv[2]
        labels = sys.argv[3].split(",")
        result = api("POST", f"/issues/{issue_num}/labels",
                      {"labels": [l.strip() for l in labels]})
        print(json.dumps(result, indent=2)[:500])
    elif cmd == "comment_issue":
        issue_num = sys.argv[2]
        body = sys.argv[3]
        result = api("POST", f"/issues/{issue_num}/comments", {"body": body})
        print(json.dumps(result, indent=2)[:500])
    elif cmd == "get_pr":
        pr_num = sys.argv[2]
        result = api("GET", f"/pulls/{pr_num}")
        print(json.dumps(result, indent=2)[:2000])
