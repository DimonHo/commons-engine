"""通用响应模型 — 与 Kotlin 侧 platform-core 对齐的契约。

设计原则：
- 所有 AI 服务端点返回统一的 ApiResponse 包装，便于核心业务层（Spring Boot）
  用统一的反序列化逻辑消费 AI 服务响应。
- error_code 采用 <service>.<reason> 格式（如 content_moderation.blocked），
  便于跨服务追踪。
"""
from typing import Any, Generic, TypeVar

from pydantic import BaseModel, Field

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    """统一的成功响应包装。"""

    success: bool = True
    data: T | None = None
    message: str | None = None


class ErrorResponse(BaseModel):
    """统一的错误响应。"""

    success: bool = False
    error_code: str = Field(description="格式: <service>.<reason>")
    message: str


class HealthResponse(BaseModel):
    """健康检查响应 — 与 Kotlin 侧 PlatformHealthController 的 {status: UP} 契约对齐。"""

    status: str = "UP"
    service: str
