# 匹配引擎（Matching Engine）

> **状态**：🚧 PoC 进行中（阶段 1） · 相关 Epic：#2

公地引擎的供需匹配模块（架构 §3.1）。把需求（乘客叫车、用户点餐）与供给（司机、骑手）匹配。

## 设计原则

- **算法可配置**：派单规则（距离优先 / 公平轮转 / 新人保护）可由合作社按区域配置，引擎不硬编码。
- **决策可解释**：每次匹配产出"为什么派给我这个单"的理由，劳动者可查、可审计。
- **反榨取约束**：内置最大匹配半径，拒绝系统性压低工资的超长空驶派单（参数最终值属治理事项）。

## 当前进度（PoC）

- [x] 领域模型 `Order / Worker / Match`（`matching_engine/models.py`）
- [x] 地理距离 `haversine_m`（`matching_engine/geo.py`）
- [x] 三种可配置策略：`DistanceFirst / FairRoundRobin / RookieProtection`
- [x] 可解释理由 + 反榨取半径
- [ ] PostGIS / Redis GEO 实时检索（依赖 #14）
- [ ] 反榨取参数 RFC（#17）

## 用法

```python
from matching_engine import DistanceFirstStrategy, MatchingEngine, Order, Worker, GeoPoint

order = Order(id="o1", origin=GeoPoint(31.2304, 121.4737))
workers = [
    Worker(id="w1", location=GeoPoint(31.2310, 121.4740), completed_orders=2),
    Worker(id="w2", location=GeoPoint(31.40, 121.50), completed_orders=500),
]
engine = MatchingEngine(DistanceFirstStrategy())
result = engine.match(order, workers)
print(result.explanation)
```

API 端点见根 `app/main.py` 的 `POST /match`。
