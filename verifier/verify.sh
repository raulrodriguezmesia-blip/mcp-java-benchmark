#!/bin/bash

# verifier.sh
# Script de evaluación determinista para el benchmark MCP de rendimiento Java

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]})/.." && pwd)"
TARGET_CODEBASE="$WORKSPACE_ROOT/target-codebase"
LOG_FILE="$WORKSPACE_ROOT/verification.log"

echo "=== Inicio de verificación determinista ===" | tee "$LOG_FILE"
echo "Timestamp: $(date -u +"%Y-%m-%d %H:%M:%S UTC")" | tee -a "$LOG_FILE"
echo "Workspace: $WORKSPACE_ROOT" | tee -a "$LOG_FILE"
echo ""

# Función para ejecutar mvn test y capturar tiempo
run_maven_test() {
    local start_time end_time elapsed_ms
    start_time=$(date +%s%3N) # milliseconds since epoch
    
    # Ejecutar mvn test, capturar salida
    local test_output
    test_output=$(mvn test -f "$TARGET_CODEBASE/pom.xml" 2>&1)
    local exit_code=$?
    
    end_time=$(date +%s%3N)
    elapsed_ms=$((end_time - start_time))
    
    echo "$exit_code" "$elapsed_ms" "$test_output"
}

# Ejecutar los tests
echo "Ejecutando mvn test en $TARGET_CODEBASE ..." | tee -a "$LOG_FILE"
read -r exit_code elapsed_ms test_output < <(run_maven_test)

echo "Código de salida: $exit_code" | tee -a "$LOG_FILE"
echo "Tiempo de ejecución: ${elapsed_ms}ms" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"

# Determinar éxito basado en código de salida
if [[ $exit_code -eq 0 ]]; then
    echo "✅ TESTS PASADOS" | tee -a "$LOG_FILE"
    success=true
else
    echo "❌ TESTS FALLIDOS" | tee -a "$LOG_FILE"
    success=false
fi

# Mostrar salida de tests (filtrada para claridad)
echo "Salida de tests:" | tee -a "$LOG_FILE"
echo "----------------" | tee -a "$LOG_FILE"
echo "$test_output" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"

# Calcular y mostrar resumen
echo "=== Resumen de verificación ===" | tee -a "$LOG_FILE"
if $success; then
    echo "Resultado: ÉXITO" | tee -a "$LOG_FILE"
else
    echo "Resultado: FALLO" | tee -a "$LOG_FILE"
fi
echo "Tiempo de ejecución: ${elapsed_ms}ms" | tee -a "$LOG_FILE"

# Salir con código apropiado
if $success; then
    exit 0
else
    exit 1
fi

