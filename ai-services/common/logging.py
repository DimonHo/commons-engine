"""结构化日志 — 统一 JSON 格式，便于 ELK / Loki 采集。

阶段 2 MVP 采用纯 stdlib logging + 简单格式化；
规模化后可平滑切换到 structlog / python-json-logger。
"""
from __future__ import annotations

import logging
import sys


def configure_logging(level: str = "INFO", service_name: str = "ai-services") -> logging.Logger:
    """配置并返回一个带服务名前缀的 logger。"""
    logger = logging.getLogger(service_name)
    if logger.handlers:
        # 已配置（如测试中多次调用）
        return logger
    logger.setLevel(getattr(logging, level.upper(), logging.INFO))
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(
        logging.Formatter(
            fmt="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
            datefmt="%Y-%m-%dT%H:%M:%S%z",
        )
    )
    logger.addHandler(handler)
    logger.propagate = False
    return logger
