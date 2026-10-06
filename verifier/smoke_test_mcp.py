#!/usr/bin/env python3
"""
Smoke test para el servidor MCP determinista (Fase 1).

Valida el contrato JSON-RPC 2.0 sobre stdio:
  1. read_source_code con file_path válido -> SUCCESS.
  2. Intento de path traversal (../README.md) -> INVALID_INPUT / PATH_TRAVERSAL.
  3. Herramienta inexistente -> INTERNAL_ERROR / UNKNOWN_TOOL.
  4. Argumento inesperado -> INVALID_INPUT / UNEXPECTED_ARGUMENT.
  5. Argumento faltante -> INVALID_INPUT / MISSING_REQUIRED_ARGUMENT.
  6. apply_code_patch round-trip (write + read back).

Uso:
  python verifier/smoke_test_mcp.py
"""
import asyncio
import json
import os
import sys

# Root del workspace (este script vive en verifier/).
WORKSPACE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MCP_SERVER = os.path.join(WORKSPACE_ROOT, "mcp-server", "server.py")

sys.path.insert(0, os.path.join(WORKSPACE_ROOT, "mcp-server"))

from mcp import ClientSession, StdioServerParameters  # noqa: E402
from mcp.client.stdio import stdio_client  # noqa: E402


async def main() -> int:
    params = StdioServerParameters(
        command=sys.executable,
        args=[MCP_SERVER],
    )
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            # 1) List tools
            tools = await session.list_tools()
            names = sorted(t.name for t in tools.tools)
            print("TOOLS:", names)
            assert names == [
                "apply_code_patch",
                "get_build_errors",
                "read_source_code",
                "run_junit_tests",
            ], names

            # 2) read_source_code SUCCESS
            res = await session.call_tool("read_source_code", {"file_path": "pom.xml"})
            env = json.loads(res.content[0].text)
            print("READ status:", env["status"], "| bytes:", env["data"]["size_bytes"])
            assert env["status"] == "SUCCESS", env
            assert env["data"]["size_bytes"] > 0
            assert env["error_details"] is None

            # 3) Path traversal must be rejected deterministically
            res = await session.call_tool("read_source_code", {"file_path": "../README.md"})
            env = json.loads(res.content[0].text)
            print("TRAVERSAL status:", env["status"], "| code:", env["error_details"]["code"])
            assert env["status"] == "INVALID_INPUT", env
            assert env["error_details"]["code"] in ("PATH_TRAVERSAL", "MALFORMED_PATH")

            # 4) Unknown argument must be rejected
            res = await session.call_tool("read_source_code", {"file_path": "pom.xml", "bogus": 1})
            env = json.loads(res.content[0].text)
            print("UNKNOWN_ARG status:", env["status"], "| code:", env["error_details"]["code"])
            assert env["status"] == "INVALID_INPUT" and env["error_details"]["code"] == "UNEXPECTED_ARGUMENT"

            # 5) Missing required argument
            res = await session.call_tool("apply_code_patch", {})
            env = json.loads(res.content[0].text)
            assert env["status"] == "INVALID_INPUT" and env["error_details"]["code"] == "MISSING_REQUIRED_ARGUMENT"
            print("MISSING_ARG status:", env["status"])

            # 6) Unknown tool
            res = await session.call_tool("does_not_exist", {})
            env = json.loads(res.content[0].text)
            assert env["status"] == "INTERNAL_ERROR" and env["error_details"]["code"] == "UNKNOWN_TOOL"
            print("UNKNOWN_TOOL status:", env["status"])

            # 7) apply_code_patch SUCCESS round-trip
            res = await session.call_tool(
                "apply_code_patch",
                {"file_path": "smoke_test_marker.txt", "new_content": "hello-deterministic-mcp"},
            )
            env = json.loads(res.content[0].text)
            print("PATCH status:", env["status"], "| created:", env["data"]["created"])
            assert env["status"] == "SUCCESS", env
            assert env["data"]["created"] is True

            # Verify the write persisted
            res = await session.call_tool("read_source_code", {"file_path": "smoke_test_marker.txt"})
            env = json.loads(res.content[0].text)
            assert env["status"] == "SUCCESS"
            assert env["data"]["content"] == "hello-deterministic-mcp"
            print("READ-VERIFY content:", env["data"]["content"])

            # Cleanup
            os.remove(os.path.join(WORKSPACE_ROOT, "target-codebase", "smoke_test_marker.txt"))
            print("SMOKE TEST PASSED")
            return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))