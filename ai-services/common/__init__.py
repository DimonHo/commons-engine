"""Commons Engine AI 服务层 — 共享内核。

为 customer-service / content-moderation / dispatch-optimizer 三个微服务
提供统一的响应模型、配置管理与结构化日志，避免重复代码。
"""
from common.models import ApiResponse, ErrorResponse, HealthResponse

__all__ = ["ApiResponse", "ErrorResponse", "HealthResponse"]
