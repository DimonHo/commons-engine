#!/usr/bin/env bash
# gh.sh — 公地引擎 GitHub API 辅助脚本
# 供审核/技术/运营三个 Agent 共用
# 用法: source scripts/gh.sh，然后调用 gh_* 函数

set -euo pipefail

REPO="DimonHo/commons-engine"
TOKEN_FILE="${COMMONS_ENGINE_TOKEN_FILE:-$HOME/.config/commons-engine/github-token}"

# ── 凭证 ──────────────────────────────────────────────
gh_token() {
    if [ -f "$TOKEN_FILE" ]; then
        cat "$TOKEN_FILE"
    elif [ -n "${GITHUB_TOKEN:-}" ]; then
        echo "$GITHUB_TOKEN"
    else
        echo ""
    fi
}

gh_api() {
    # 用法: gh_api "GET|POST|PATCH" "/repos/.../issues" '{"json":"body"}'
    local method="$1"; shift
    local endpoint="$1"; shift
    local data="${1:-}"
    local token; token=$(gh_token)
    if [ -z "$token" ]; then
        echo "ERROR: 无 GitHub token。请创建 PAT (scope: repo) 存到 $TOKEN_FILE" >&2
        return 1
    fi
    local args=(-s -X "$method" \
        -H "Authorization: token $token" \
        -H "Accept: application/vnd.github.v3+json")
    if [ -n "$data" ]; then
        args+=(-H "Content-Type: application/json" -d "$data")
    fi
    curl "${args[@]}" "https://api.github.com/repos/${REPO}${endpoint}"
}

# ── Issue 操作 ─────────────────────────────────────────
gh_list_issues()      { gh_api GET "/issues?state=open&sort=created&direction=desc&per_page=${1:-30}"; }
gh_list_recent_issues() { gh_api GET "/issues?state=all&sort=created&direction=desc&per_page=${1:-10}"; }
gh_get_issue()        { gh_api GET "/issues/$1"; }
gh_comment_issue()    { gh_api POST "/issues/$1/comments" "{\"body\":\"$(echo "$2" | jq -Rs .)\"}"; }
gh_label_issue()      { gh_api POST "/issues/$1/labels" "{\"labels\":$(echo "$2" | jq -R 'split(\",\")')"; }

# ── PR 操作 ────────────────────────────────────────────
gh_list_prs()         { gh_api GET "/pulls?state=open&sort=created&direction=desc&per_page=${1:-20}"; }
gh_get_pr()           { gh_api GET "/pulls/$1"; }
gh_pr_diff()          { gh_api GET "/pulls/$1" | jq -r '.diff_url' | xargs curl -sL; }
gh_review_pr()        { gh_api POST "/pulls/$1/reviews" "{\"event\":\"$2\",\"body\":\"$(echo "$3" | jq -Rs .)\"}"; }

# ── Discussion 操作（需 GraphQL）──────────────────────
gh_list_discussions() {
    local token; token=$(gh_token)
    if [ -z "$token" ]; then return 1; fi
    curl -s -X POST -H "Authorization: bearer $token" \
        -H "Content-Type: application/json" \
        -d '{"query":"{ repository(owner:\"DimonHo\", name:\"commons-engine\") { discussions(first:10, orderBy:{field:UPDATED_AT, direction:DESC}) { nodes { number title body comments{totalCount} } } } }"}' \
        https://api.github.com/graphql
}

# ── 仓库统计 ───────────────────────────────────────────
gh_stats() {
    python3 -c "
import json, sys, subprocess
issues = json.loads(subprocess.check_output(['bash','-c','source scripts/gh.sh; gh_list_issues 100']))
prs = [i for i in issues if 'pull_request' in i]
real_issues = [i for i in issues if 'pull_request' not in i]
print(f'Open Issues: {len(real_issues)}')
print(f'Open PRs: {len(prs)}')
for i in real_issues[:5]:
    print(f'  - #{i[\"number\"]} {i[\"title\"]}')
for p in prs[:5]:
    print(f'  - PR #{p[\"number\"]} {p[\"title\"]}')
"
}

# ── 检测凭证是否就绪 ──────────────────────────────────
gh_check() {
    local token; token=$(gh_token)
    if [ -z "$token" ]; then
        echo "NO_TOKEN: 需要配置 GitHub token"
        return 1
    fi
    # NOTE: gh_api 的 curl 输出多行 JSON，用 head -1 会在 curl 仍写入时关闭管道，
    # 触发 SIGPIPE → 非0 退出码 → 在本文件顶层 `set -o pipefail` 下使整个函数非0 返回。
    # 修复：在此管道局部关闭 pipefail（各 Agent 的 gh_check 守卫即可正常工作）。
    local result
    set +o pipefail
    result=$(gh_api GET "" 2>&1 | sed 1q)
    set -o pipefail
    if echo "$result" | grep -q "Bad credentials"; then
        echo "BAD_TOKEN: token 无效或已过期"
        return 1
    fi
    echo "OK: token 有效"
    return 0
}
