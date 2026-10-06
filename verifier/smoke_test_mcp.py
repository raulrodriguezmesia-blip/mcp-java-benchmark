#!/usr/bin/env python3
"""
Smoke test para el servidor MCP determinista (Fase 1).

Valida el contrato JSON-RPC 2.0 sobre stdio:
  1. read_source_code con file_path valido -> SUCCESS.
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
import logging
import os
import sys
from pathlib import Path

# Configuracion de rutas usando pathlib
WORKSPACE_ROOT = Path(__file__).resolve().parent.parent
MCP_SERVER_DIR = WORKSPACE_ROOT / "mcp-server"
MCP_SERVER_SCRIPT = MCP_SERVER_DIR / "server.py"
TARGET_CODEBASE = WORKSPACE_ROOT / "target-codebase"

# Garantizar resolucion local de paquetes en sys.path
sys.path.insert(0, str(MCP_SERVER_DIR))
sys.path.insert(0, str(WORKSPACE_ROOT))

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

logging.basicConfig(level=logging.INFO)


def parse_mcp_response(res):
    """Parsea la respuesta MCP de forma defensiva."""
    raw = res.content[0].text if res.content and hasattr(res.content[0], "text") else ""
    if raw.strip().startswith("{"):
        try:
            parsed = json.loads(raw)
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError:
            return {"status": "ERROR", "raw": raw}
    return {"status": "SUCCESS", "raw": raw}


def assert_status(response, expected_status, expected_code=None, label="assert"):
    """Verifica que una respuesta MCP tenga status y opcionalmente un error code concreto."""
    env = parse_mcp_response(response) or {"status": "ERROR"}
    status = env.get("status")
    
    # Manejo seguro cuando error_details es None o no existe
    error_details = env.get("error_details") or {}
    err_code = error_details.get("code", "") if isinstance(error_details, dict) else ""

    assert status == expected_status, f"{label}: expected {expected_status}, got {env}"
    if expected_code is not None:
        assert err_code == expected_code, f"{label}: expected code {expected_code}, got {env}"
    return env


async def main() -> int:
    # Configurar PYTHONPATH explicito para el subproceso stdio
    env_vars = dict(os.environ)
    env_path = [str(WORKSPACE_ROOT), str(MCP_SERVER_DIR)]
    if "PYTHONPATH" in env_vars:
        env_path.append(env_vars["PYTHONPATH"])
    env_vars["PYTHONPATH"] = os.pathsep.join(env_path)

    # Determinar si el entrypoint es server.py o el modulo mcp_server
    if MCP_SERVER_SCRIPT.exists():
        mcp_args = [str(MCP_SERVER_SCRIPT)]
    else:
        mcp_args = ["-m", "mcp_server.server", "--target-codebase", str(TARGET_CODEBASE)]

    params = StdioServerParameters(
        command=sys.executable,
        args=mcp_args,
        cwd=str(WORKSPACE_ROOT),
        env=env_vars,
    )

    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            # 1) List tools
            tools = await session.list_tools()
            names = sorted(t.name for t in tools.tools)
            expected_tools = [
                "apply_code_patch",
                "get_build_errors",
                "read_source_code",
                "run_junit_tests",
            ]
            print("TOOLS:", names)
            assert names == expected_tools, f"Unexpected tool list: {names}"

            # 2) read_source_code SUCCESS
            res = await session.call_tool("read_source_code", {"file_path": "pom.xml"})
            env = assert_status(res, "SUCCESS", label="read_source_code valid")
            size_bytes = (env.get("data") or {}).get("size_bytes", 0)
            print("READ status:", env.get("status"), "| bytes:", size_bytes)
            assert size_bytes > 0, f"Expected size_bytes > 0, got {size_bytes}"

            # 3) Path traversal rejection
            res = await session.call_tool("read_source_code", {"file_path": "../README.md"})
            env = assert_status(res, "INVALID_INPUT", label="read_source_code traversal")
            error_details = env.get("error_details") or {}
            err_code = error_details.get("code", "") if isinstance(error_details, dict) else ""
            print("TRAVERSAL status:", env.get("status"), "| code:", err_code)
            assert err_code in ("PATH_TRAVERSAL", "MALFORMED_PATH"), (
                f"Expected PATH_TRAVERSAL or MALFORMED_PATH, got {err_code}"
            )

            # 4) Unknown argument rejection
            res = await session.call_tool("read_source_code", {"file_path": "pom.xml", "bogus": 1})
            env = assert_status(res, "INVALID_INPUT", label="unknown argument")
            error_details = env.get("error_details") or {}
            err_code = error_details.get("code", "") if isinstance(error_details, dict) else ""
            print("UNKNOWN_ARG status:", env.get("status"), "| code:", err_code)
            assert err_code == "UNEXPECTED_ARGUMENT", (
                f"Expected UNEXPECTED_ARGUMENT, got {err_code}"
            )

            # 5) Missing required argument
            res = await session.call_tool("apply_code_patch", {})
            env = assert_status(res, "INVALID_INPUT", label="missing required argument")
            error_details = env.get("error_details") or {}
            err_code = error_details.get("code", "") if isinstance(error_details, dict) else ""
            print("MISSING_ARG status:", env.get("status"), "| code:", err_code)
            assert err_code == "MISSING_REQUIRED_ARGUMENT", (
                f"Expected MISSING_REQUIRED_ARGUMENT, got {err_code}"
            )

            # 6) Unknown tool
            res = await session.call_tool("does_not_exist", {})
            env = assert_status(res, "INTERNAL_ERROR", label="unknown tool")
            error_details = env.get("error_details") or {}
            err_code = error_details.get("code", "") if isinstance(error_details, dict) else ""
            print("UNKNOWN_TOOL status:", env.get("status"), "| code:", err_code)
            assert err_code == "UNKNOWN_TOOL", f"Expected UNKNOWN_TOOL, got {err_code}"

            # 7) apply_code_patch round-trip
            marker_file = "smoke_test_marker.txt"
            marker_content = "hello-deterministic-mcp"

            res = await session.call_tool(
                "apply_code_patch",
                {"file_path": marker_file, "new_content": marker_content},
            )
            env = assert_status(res, "SUCCESS", label="apply_code_patch create")
            created = (env.get("data") or {}).get("created")
            print("PATCH status:", env.get("status"), "| created:", created)
            assert created is True, f"Expected created=True, got {created}"

            # Verify persisted content
            res = await session.call_tool("read_source_code", {"file_path": marker_file})
            env = assert_status(res, "SUCCESS", label="read back patched content")
            content = (env.get("data") or {}).get("content")
            print("READ-VERIFY content:", content)
            assert content == marker_content, (
                f"Expected content {marker_content!r}, got {content!r}"
            )

            # Cleanup
            marker_path = TARGET_CODEBASE / marker_file
            if marker_path.exists():
                marker_path.unlink()
                print("Cleanup: removed", marker_file)

            print("SMOKE TEST PASSED")
            return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
