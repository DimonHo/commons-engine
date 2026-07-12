## 评审意见 — PR #56: fix(scripts) gh.sh Authorization header 修复

### 总体评价

✅ 关键修复。根因分析准确，修复方案正确。

### 根因确认

diff 中可以看到 `gh_api` 和 `gh_list_discussions` 的 Authorization header 确实使用了字面量占位符 `***` 而非 `$(gh_token)` 变量。这导致自 7/9 起所有 GitHub API 调用静默失败——请求发出但认证无效，GitHub 返回 401 但 curl `-s` 模式下不报错。

### 修复审查

1. **`gh_check()` 重写** — 从 grep "Bad credentials" 改为用 `/user` 端点的 HTTP 状态码检测（200=OK / 401=BAD_TOKEN / 其他=UNKNOWN），更健壮。新的实现注释清晰，解释了为何放弃旧的 grep 方案

2. **`_create_issues_shell1.py`** — 新增的一次性脚本，用于批量创建 #51-#54 Issue。已通过 `.gitignore` 的 `scripts/_*.py` 规则排除。但此文件实际提交到了 diff 中，说明它是在 `.gitignore` 规则添加之前提交的，或者被显式 `git add` 了。

### 需关注

1. **一次性脚本残留** — `_create_issues_shell1.py` 是临时脚本，已被 `.gitignore` 的 `_*.py` 规则覆盖（说明不应入库），但实际已提交。建议后续清理或接受为历史记录

2. **token 在脚本中** — `_create_issues_shell1.py` 第 12 行从文件读取 token，第 30 行传递给 curl header。diff 显示为 `-H f"Authorization: token ***"`——这里 `***` 看起来是 redacted 后的显示。如果是字面量 `***` 则脚本无法工作；如果是 f-string 引用变量，则正确。需要确认实际代码中此处引用了 `token` 变量

### 安全

- ✅ token 从文件读取，不硬编码
- ⚠️ 需确认 `_create_issues_shell1.py` 中 Authorization header 是否实际使用了变量

### 结论

关键 Bug 修复，方案正确。建议合并（由人类维护者决定）。

— Commons Engine Bot (AI)
