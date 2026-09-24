# Java Algorithmic Bottleneck MCP Benchmark (O(N^2) -> O(N))

A reproducible, high-performance Java benchmark suite designed to evaluate AI Agent efficacy in identifying, patching, and verifying O(N^2) algorithmic bottlenecks using Model Context Protocol (MCP) servers.

[![Build Status](https://img.shields.io/badge/Verification-PASS-success)](https://github.com/raulrodriguezmesia-blip/mcp-java-benchmark)
[![Java Version](https://img.shields.io/badge/Java-17-blue)](https://pom.xml)
[![MCP Protocol](https://img.shields.io/badge/MCP-stdio--compliant-orange)](https://modelcontextprotocol.io)
[![Tag](https://img.shields.io/badge/Release-v1.0.0--passed-brightgreen)](https://github.com/raulrodriguezmesia-blip/mcp-java-benchmark/releases/tag/v1.0.0-passed)

---

## Executive Summary

This benchmark evaluates whether an AI Agent can orchestrate MCP tools to:
* Inspect a Java codebase (`target-codebase`) experiencing an O(N^2) performance bottleneck on large dataset deduplication (500,000 records).
* Diagnose high latency (>120s timeout) caused by linear lookups on unindexed collections.
* Apply a production-grade O(N) refactor using pre-allocated `LinkedHashSet` capacity.
* Execute a deterministic verification harness (`verify.sh`) to confirm SLA compliance (<500ms).

---

## Architecture & Components

```text
mcp-java-benchmark/
├── benchmarks/
│   └── task_01_performance/
│       ├── GoldenOrderProcessor.java  # O(N) Reference Solution (<50ms)
│       └── task.json                  # Task metadata & acceptance criteria
├── docs/
│   └── EROS_EVALUATION_REPORT.md     # EROS agent quantitative performance report
├── mcp-server/
│   ├── server.py                      # JSON-RPC 2.0 stdio-compliant MCP server
│   └── requirements.txt               # MCP Python dependencies
├── target-codebase/                   # Java 17 / Maven target workspace
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/sandbox/service/OrderProcessor.java
│       └── test/java/com/sandbox/service/OrderProcessorTest.java
├── verifier/
│   └── verify.sh                      # Deterministic bash test harness
└── Dockerfile                         # Multi-stage isolated evaluation environment

---
```
## Technical Specification: O(N^2) -> O(N) Bottleneck

### Baseline (Failure State)
The target codebase initially performs iterative containment checks over raw collections, resulting in quadratic time complexity:
- Time Complexity = O(N^2) -> (500,000)^2 = 2.5 x 10^11 operations
- Result: Timed out after 120,000 ms (EXIT CODE 1).

### Golden Solution (Passed State)
The optimized implementation achieves linear execution by allocating exact memory buckets to eliminate JVM heap array resizing and rehashing overhead:

```java
package com.sandbox.service;

import java.util.*;

public class OrderProcessor {

    public List<String> filterUniqueOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        int size = orderIds.size();
        // Pre-allocate capacity using 0.75 load factor to prevent rehashing
        int initialCapacity = Math.max((int) (size / 0.75f) + 1, 16);
        Set<String> uniqueOrders = new LinkedHashSet<>(initialCapacity);
        
        for (int i = 0; i < size; i++) {
            uniqueOrders.add(orderIds.get(i));
        }
        
        // Exact allocation for return list (Zero intermediate array copies)
        List<String> result = new ArrayList<>(uniqueOrders.size());
        result.addAll(uniqueOrders);
        return result;
    }
}
Execution Time: < 50ms algorithmic processing (244ms total test suite runtime).

Memory Bounds: O(N) auxiliary space with bounded garbage collection pauses.

MCP Server Tools Interface
The stdio-compliant Python MCP server (mcp-server/server.py) exposes four core tools for AI Agent orchestration:

Tool Name	Parameters	Description
get_task_info	None	Reads task.json metadata and SLA guidelines.
read_code	file_path	Inspects target codebase source files.
apply_patch	file_path, content	Overwrites source file with agent-generated patch.
run_verification	None	Invokes verifier/verify.sh to run mvn test and returns exit codes.
Reproduction & Verification
Local Environment
Bash
# 1. Clone Repository
git clone [https://github.com/raulrodriguezmesia-blip/mcp-java-benchmark.git](https://github.com/raulrodriguezmesia-blip/mcp-java-benchmark.git)
cd mcp-java-benchmark

# 2. Run Deterministic Verification Harness
bash verifier/verify.sh
Isolated Docker Container
Bash
# Build & Execute Multi-Stage Verification Container
docker build -t mcp-java-benchmark .
docker run --rm mcp-java-benchmark
Benchmark Metrics & Verification Results
Pass@1 Criteria: Code compiles cleanly under JDK 17, passes all functional unit tests, and completes 500,000-record processing within a 500ms timeout window.

Verification Status: SUCCESS (Exit Code 0, Execution Time: ~9.0s end-to-end including Maven overhead).

Git Version Tag: v1.0.0-passed
