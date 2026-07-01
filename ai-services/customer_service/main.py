"""Commons Engine AI 服务层 — 智能客服入口。"""
from fastapi import FastAPI

app = FastAPI(title="Commons Engine AI Services", version="0.1.0")


@app.get("/health")
async def health():
    return {"status": "UP", "service": "ai-services"}


@app.post("/api/v1/customer-service/chat")
async def chat(message: str):
    """简单 FAQ 路由 — MVP 阶段后续接入 NLP 模型。"""
    return {
        "reply": f"已收到您的消息：{message}。智能客服功能开发中，复杂问题将转人工处理。",
        "needsHuman": False,
    }
