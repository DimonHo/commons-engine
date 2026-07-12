#!/usr/bin/env python3
"""gh_review.py — Review helper for Commons Engine Bot (no jq dependency).

Usage:
  python3 scripts/gh_review.py diff <PR_NUMBER>     # Print PR diff to stdout
  python3 scripts/gh_review.py review <PR_NUMBER> <EVENT> <BODY_FILE>
  python3 scripts/gh_review.py label <ISSUE_NUMBER> <LABEL>
"""
import json
import os
import subprocess
import sys
import urllib.request
import urllib.error

REPO = "DimonHo/commons-engine"
TOKEN_FILE = os.environ.get(
    "COMMONS_ENGINE_TOKEN_FILE",
    os.path.expanduser("~/.config/commons-engine/github-token"),
)


def gh_token():
    if os.path.isfile(TOKEN_FILE):
        with open(TOKEN_FILE) as f:
            return f.read().strip()
    return os.environ.get("GITHUB_TOKEN", "")


def api(method, endpoint, data=None):
    token = gh_token()
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
        body = json.dumps(data).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())


def get_diff(pr_number):
    """Fetch diff using the Accept header trick (no jq needed)."""
    token = gh_token()
    url = f"https://api.github.com/repos/{REPO}/pulls/{pr_number}"
    req = urllib.request.Request(url)
    req.add_header("Authorization", f"token {token}")
    req.add_header("Accept", "application/vnd.github.v3.diff")
    with urllib.request.urlopen(req) as resp:
        sys.stdout.buffer.write(resp.read())


def review_pr(pr_number, event, body):
    result = api("POST", f"/pulls/{pr_number}/reviews",
                 {"event": event, "body": body})
    return result


def label_issue(issue_number, label):
    result = api("POST", f"/issues/{issue_number}/labels", {"labels": [label]})
    return result


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "diff":
        get_diff(int(sys.argv[2]))
    elif cmd == "review":
        pr_number = int(sys.argv[2])
        event = sys.argv[3]
        body_file = sys.argv[4]
        with open(body_file) as f:
            body = f.read()
        result = review_pr(pr_number, event, body)
        if "id" in result:
            print(f"OK: review posted on PR #{pr_number}")
        else:
            print(f"ERROR: {json.dumps(result)}", file=sys.stderr)
            sys.exit(1)
    elif cmd == "label":
        issue_number = int(sys.argv[2])
        label = sys.argv[3]
        result = label_issue(issue_number, label)
        if isinstance(result, list) and result:
            print(f"OK: labeled #{issue_number} with '{label}'")
        else:
            print(f"ERROR: {json.dumps(result)}", file=sys.stderr)
            sys.exit(1)
    else:
        print(f"Unknown command: {cmd}", file=sys.stderr)
        sys.exit(1)
