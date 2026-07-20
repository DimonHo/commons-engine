"""配置管理 — 基于 pydantic-settings，支持环境变量注入。

每个微服务在启动时实例化对应的 Settings 子类，从环境变量读取配置。
生产部署时通过 docker-compose / K8s ConfigMap 注入。
"""
from __future__ import annotations

from pydantic import BaseModel


class BaseSettings(BaseModel):
    """所有 AI 服务共享的基础配置项。"""

    # 核心业务层地址（Spring Boot app 模块）
    core_backend_url: str = "http://localhost:8080"
    # 日志级别
    log_level: str = "INFO"
