#!/usr/bin/env python3
"""Post a PR review to GitHub."""
import json
import subprocess
import sys
import urllib.request

def gh_token():
    return subprocess.check_output(
        ['bash', '-c', 'cd /opt/data/home/commons-engine && source scripts/gh.sh && gh_token']
    ).decode().strip()

def main():
    pr_number = sys.argv[1]
    review_file = sys.argv[2]

    with open(review_file, 'r', encoding='utf-8') as f:
        review_body = f.read()

    token = gh_token()

    payload = json.dumps({
        'event': 'COMMENT',
        'body': review_body
    })

    req = urllib.request.Request(
        f'https://api.github.com/repos/DimonHo/commons-engine/pulls/{pr_number}/reviews',
        data=payload.encode('utf-8'),
        headers={
            'Authorization': f'token {token}',
            'Accept': 'application/vnd.github.v3+json',
            'Content-Type': 'application/json'
        },
        method='POST'
    )
    resp = urllib.request.urlopen(req)
    result = json.loads(resp.read())
    print(f'Review posted: ID={result["id"]}, state={result["state"]}')
    print(f'URL: {result["html_url"]}')

if __name__ == '__main__':
    main()
