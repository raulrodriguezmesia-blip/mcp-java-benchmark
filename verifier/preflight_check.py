import sys
import subprocess
import shutil

def check_python():
    v = sys.version_info
    if v >= (3, 10):
        print(f"OK Python {v.major}.{v.minor}.{v.micro} (min: 3.10)")
        return True
    print(f"ERROR Python {v.major}.{v.minor} no soportado (requiere >= 3.10)")
    return False

def check_java():
    try:
        proc = subprocess.run(["java", "-version"], capture_output=True, text=True, timeout=10)
        output = proc.stderr or proc.stdout
        first_line = output.splitlines()[0] if output else ""
        print(f"INFO Java {first_line} (JDK 17 requerido; se compila con --release 17)")
        return True
    except Exception as e:
        print(f"ERROR al detectar Java: {e}")
        return False

def _find_mvn():
    candidates = ["mvn.cmd", "mvn.bat", "mvn"] if sys.platform == "win32" else ["mvn"]
    for name in candidates:
        if shutil.which(name):
            return name
    return None

def check_maven():
    exe = _find_mvn()
    if not exe:
        print("ERROR Maven no esta instalado o no se encuentra en el PATH")
        return False
    try:
        proc = subprocess.run([exe, "-version"], capture_output=True, text=True, timeout=10, shell=(sys.platform == "win32"))
        if proc.returncode == 0:
            first_line = proc.stdout.splitlines()[0] if proc.stdout else ""
            print(f"OK Maven detectado ({first_line.strip()})")
            return True
        print(f"ERROR Maven devolvio error (code {proc.returncode})")
        return False
    except Exception as e:
        print(f"ERROR al ejecutar Maven: {e}")
        return False

def main():
    print("=== Pre-flight check: mcp-java-benchmark (Fase 1) ===")
    p_ok = check_python()
    j_ok = check_java()
    m_ok = check_maven()
    
    if p_ok and j_ok and m_ok:
        print("\n>>> Pre-flight check completado con exito.")
        return 0
    else:
        print("\n>>> Pre-flight check fallo.")
        return 1

if __name__ == "__main__":
    sys.exit(main())
