# Arquitectura del Sistema: mcp-java-benchmark

## 1. Diagrama de Arquitectura del Sistema

```text
+------------------+         JSON-RPC 2.0 (stdio)         +----------------------+
|                  | <----------------------------------+ |                      |
|  LLM Agent /     |                                    | |  MCP Server (Python) |
|  Client          | +----------------------------------> |  mcp-server/server.py|
+------------------+  Response (Unified Envelope JSON)    +----------+-----------+
|
Subprocess / File IO
v
+----------------------+
|  Target Codebase     |
|  (JVM 17 / Maven)    |
+----------+-----------+
|
+----------------------+----------------------+
|                                             |
v                                             v
+-----------------------+                     +-----------------------+
| OrderProcessor        |                     | MetricsCollector      |
| (LinkedHashSet O(N))  |                     | (ConcurrentHashMap /  |
+-----------------------+                     |  LongAdder)           |
+-----------------------+
```

## 2. Métricas de Rendimiento y Tabla de Complejidad

| Componente / Operación | Implementación Base (Antes) | Implementación Optimizada (Ahora) | Complejidad Temporal | Complejidad Espacial | SLA / Resultado Medido |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Deduplicación de Pedidos** (`OrderProcessor`) | *Re-hashing* frecuente, capacidad por defecto | `LinkedHashSet` con capacidad precalculada `⌈N / 0.75⌉ + 1` | $O(N)$ | $O(N)$ | **< 500 ms** para 100,000 elementos |
| **Métricas Concurrentes** (`MetricsCollector`) | Sincronización pesada / Puntos de contención | `LongAdder` + `ConcurrentHashMap` con `computeIfAbsent` | $O(1)$ amortizado | $O(K)$ categorías | **0% pérdida** en 16 hilos × 50,000 ops |
| **Manejo de Errores MCP** | Respuestas no estandarizadas | Envoltorio JSON determinista (`SUCCESS`, `INVALID_INPUT`, `INTERNAL_ERROR`) | $O(1)$ | $O(1)$ | Validaciones en ms con protección Path Traversal |

## 3. Instrucciones de Despliegue y Ejecución

### Ejecución Local de Pruebas (Maven)
```bash
cd target-codebase
mvn clean test -B
```

### Inicio del Servidor MCP
```bash
python -m venv venv
source venv/bin/activate
pip install -r mcp-server/requirements.txt
python mcp-server/server.py
```

### Despliegue Cloud-Native (Docker Compose)
```bash
docker compose up --build
```