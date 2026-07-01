"""不依赖任何第三方库的冒烟测试（匹配模块为纯 stdlib）。"""
import sys
sys.path.insert(0, "packages/matching-engine")

from matching_engine import (
    DistanceFirstStrategy, FairRoundRobinStrategy, GeoPoint,
    MatchingEngine, Order, RookieProtectionStrategy, Worker,
)

P = GeoPoint(31.2304, 121.4737)  # 上海人民广场
w_near = Worker("near", GeoPoint(31.2310, 121.4740), completed_orders=2)
w_far = Worker("far", GeoPoint(31.40, 121.50), completed_orders=500)
w_rookie = Worker("rookie", GeoPoint(31.2312, 121.4741), completed_orders=1)
order = Order("o-demo", P)

print("== 距离优先 ==")
r = MatchingEngine(DistanceFirstStrategy()).match(order, [w_far, w_near])
print("  选中:", r.worker_id, "|", r.explanation.replace("\n", " "))

print("== 新人保护 ==")
r = MatchingEngine(RookieProtectionStrategy()).match(order, [w_far, w_rookie])
print("  选中:", r.worker_id, "|", r.reasons[0])

print("== 公平轮转 ==")
s = FairRoundRobinStrategy(); eng = MatchingEngine(s)
seq = [eng.match(order, [w_near, w_far, w_rookie]).worker_id for _ in range(6)]
print("  序列:", seq)

print("== 反榨取半径（北京司机不派单）==")
bj = Worker("bj", GeoPoint(39.9042, 116.4074))
r = MatchingEngine(DistanceFirstStrategy()).match(order, [bj])
print("  结果:", "None（拒绝超长空驶）" if r is None else r.worker_id)

assert r is None
# 注意：'far'(~18km) 被反榨取半径(5km)过滤，范围内仅 near+rookie 轮转
assert seq == ["near", "rookie", "near", "rookie", "near", "rookie"], seq
print("\n✅ 匹配引擎 PoC 冒烟测试全部通过（含反榨取半径过滤验证）")
