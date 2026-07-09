"""手动 lint 检查（替代 ruff，纯 stdlib）：行长 + 残留调试符号。"""
import pathlib

LIMIT = 100
problems = []
for f in pathlib.Path(".").rglob("*.py"):
    if ".venv" in f.parts:
        continue
    for i, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
        if len(line) > LIMIT:
            problems.append(f"{f}:{i} 长度 {len(line)} > {LIMIT}: {line[:60]}...")
    txt = f.read_text(encoding="utf-8")
    for bad in ["print(", "# TODO", "breakpoint()", "pdb"]:
        if bad in txt:
            problems.append(f"{f} 含可疑符号: {bad}")

if problems:
    print("⚠️ 发现 %d 处问题:" % len(problems))
    for p in problems:
        print("  " + p)
else:
    print("✅ 全部 .py 文件：行长 ≤ %d，无可疑符号" % LIMIT)
