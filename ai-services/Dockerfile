# AI 服务层基础镜像——三个微服务共用
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# 默认启动智能客服；其他服务通过 CMD 覆盖
# docker run ... uvicorn content_moderation.main:app --host 0.0.0.0 --port 8002
# docker run ... uvicorn dispatch_optimizer.main:app --host 0.0.0.0 --port 8003
EXPOSE 8000
CMD ["uvicorn", "customer_service.main:app", "--host", "0.0.0.0", "--port", "8000"]
