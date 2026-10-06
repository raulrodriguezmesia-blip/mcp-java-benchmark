# syntax=docker/dockerfile:1.7
#
# Deterministic MCP server container (Python, stdio transport).
# Non-root user, no pip cache, minimal layers.

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN addgroup --system mcp && adduser --system --ingroup mcp mcp
USER mcp

WORKDIR /opt/mcp-server
COPY requirements.txt .
RUN pip install --no-cache-dir --disable-pip-version-check -r requirements.txt

COPY server.py .

CMD ["python", "server.py"]