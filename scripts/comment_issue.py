#!/usr/bin/env python3
"""Post a comment to a GitHub issue/PR via the API.

Usage: python3 comment_issue.py <number> <body-file>
"""
import json
import os
import sys
import urllib.request

REPO = "DimonHo/commons-engine"
TOKEN_FILE = os.environ.get(
    "COMMONS_ENGINE_TOKEN_FILE",
    os.path.expanduser("~/.config/commons-engine/github-token"),
)


def get_token():
    if os.path.isfile(TOKEN_FILE):
        with open(TOKEN_FILE) as f:
            return f.read().strip()
    env = os.environ.get("GITHUB_TOKEN", "").strip()
    if env:
        return env
    return None


def main():
    if len(sys.argv) < 3:
        print("usage: comment_issue.py <number> <body-file>", file=sys.stderr)
        sys.exit(2)
    number = sys.argv[1]
    with open(sys.argv[2], encoding="utf-8") as f:
        body = f.read()

    token = get_token()
    if not token:
        print("NO_TOKEN", file=sys.stderr)
        sys.exit(1)

    payload = json.dumps({"body": body}).encode("utf-8")
    url = f"https://api.github.com/repos/{REPO}/issues/{number}/comments"
    req = urllib.request.Request(
        url,
        data=payload,
        method="POST",
        headers={
            "Authorization": f"token {token}",
            "Accept": "application/vnd.github.v3+json",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        print(f"OK: commented on #{number} - {data.get('html_url')}")
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
