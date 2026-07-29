#!/usr/bin/env python3
"""
update_agent_context.py — Regenera el bloque de contexto del archivo de agente
(Claude.md / AGENTS.md) a partir de los artefactos de specs/.

Objetivo: que el contexto del agente refleje el estado real de la
especificacion sin revisarlo a mano en cada sesion.

Preserva todo lo escrito a mano: solo reemplaza el bloque delimitado por
    <!-- SPEC-KIT:BEGIN --> ... <!-- SPEC-KIT:END -->
Si el bloque no existe, lo agrega al final.

Uso:
    python scripts/update_agent_context.py
    python scripts/update_agent_context.py --check     # no escribe, sale 1 si hay cambios
    python scripts/update_agent_context.py --spec 001-lsa-sign-translator

Pensado para correr a mano, en un pre-commit hook, o en CI.
Cross-platform (Windows / Linux): sin dependencias externas.
"""

from __future__ import annotations

import argparse
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

BEGIN = "<!-- SPEC-KIT:BEGIN - generado por update_agent_context.py, no editar a mano -->"
END = "<!-- SPEC-KIT:END -->"

# Archivos de agente candidatos, en orden de preferencia
AGENT_FILES = ["CLAUDE.md", "Claude.md", "AGENTS.md"]

# Patrones de identificadores de requisito/decision
ID_PATTERNS = {
    "FR": re.compile(r"\bFR-(\d+)\b"),
    "NFR": re.compile(r"\bNFR-(\d+)\b"),
    "SC": re.compile(r"\bSC-(\d+)\b"),
    "US": re.compile(r"\bUS(\d+)\b"),
    "DD": re.compile(r"\bDD-(\d+)\b"),
    "R": re.compile(r"\bR-(\d+)\b"),
    "T": re.compile(r"\bT(\d{3})\b"),
}

ARTIFACTS = [
    ("spec.md", "Especificacion"),
    ("plan.md", "Plan tecnico"),
    ("research.md", "Decisiones y trade-offs"),
    ("data-model.md", "Modelo de datos"),
    ("quickstart.md", "Validacion E2E"),
    ("tasks.md", "Plan de ejecucion"),
]


def repo_root(start: Path) -> Path:
    """Sube hasta encontrar .git o .specify."""
    p = start.resolve()
    for cand in [p, *p.parents]:
        if (cand / ".git").exists() or (cand / ".specify").exists():
            return cand
    return p


def find_spec_dir(root: Path, nombre: str | None) -> Path | None:
    specs = root / "specs"
    if not specs.is_dir():
        return None
    if nombre:
        d = specs / nombre
        return d if d.is_dir() else None
    # sin nombre: la carpeta con prefijo numerico mas alto
    dirs = [d for d in specs.iterdir() if d.is_dir() and re.match(r"^\d{3}-", d.name)]
    if not dirs:
        return None
    return sorted(dirs, key=lambda d: d.name)[-1]


def find_agent_file(root: Path) -> Path | None:
    for nombre in AGENT_FILES:
        p = root / nombre
        if p.exists():
            return p
    return None


def leer(p: Path) -> str:
    try:
        return p.read_text(encoding="utf-8")
    except Exception:
        return ""


def contar_ids(texto: str) -> dict[str, int]:
    """Cuenta identificadores unicos por tipo."""
    out = {}
    for k, pat in ID_PATTERNS.items():
        encontrados = set(pat.findall(texto))
        if encontrados:
            out[k] = len(encontrados)
    return out


def extraer_titulados(texto: str, prefijo: str, limite: int = 40) -> list[str]:
    """Extrae lineas del tipo 'DD-002: titulo' o '### DD-002 titulo'.
    Devuelve strings normalizados 'DD-002 — titulo'."""
    patron = re.compile(
        rf"^\s*#*\s*\**({prefijo}-\d+)\**\s*[:\-—]?\s*(.+?)\s*$",
        re.MULTILINE,
    )
    vistos: dict[str, str] = {}
    for m in patron.finditer(texto):
        ident, titulo = m.group(1), m.group(2)
        titulo = re.sub(r"[*`#]", "", titulo).strip()
        titulo = re.sub(r"\s+", " ", titulo)
        if len(titulo) > 90:
            titulo = titulo[:87] + "..."
        # nos quedamos con la primera aparicion con titulo util
        if ident not in vistos and len(titulo) > 3:
            vistos[ident] = titulo
    return [f"{k} — {v}" for k, v in sorted(vistos.items())][:limite]


def progreso_tareas(texto: str) -> tuple[int, int]:
    """Cuenta checkboxes marcados y totales en tasks.md."""
    hechas = len(re.findall(r"^\s*[-*]\s*\[[xX]\]", texto, re.MULTILINE))
    pend = len(re.findall(r"^\s*[-*]\s*\[ \]", texto, re.MULTILINE))
    return hechas, hechas + pend


def extraer_seccion(texto: str, titulos: list[str], max_lineas: int = 12) -> list[str]:
    """Extrae bullets bajo un heading cuyo texto contenga alguno de los titulos."""
    lineas = texto.splitlines()
    out: list[str] = []
    capturando = False
    for ln in lineas:
        if re.match(r"^\s*#{1,6}\s", ln):
            encabezado = ln.lower()
            if capturando:
                break
            capturando = any(t.lower() in encabezado for t in titulos)
            continue
        if capturando:
            m = re.match(r"^\s*[-*]\s+(.*)", ln)
            if m:
                item = re.sub(r"[*`]", "", m.group(1)).strip()
                if item:
                    out.append(item if len(item) <= 110 else item[:107] + "...")
            if len(out) >= max_lineas:
                break
    return out


def construir_bloque(root: Path, spec_dir: Path) -> str:
    rel = spec_dir.relative_to(root).as_posix()
    ahora = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    L: list[str] = []
    L.append(BEGIN)
    L.append("")
    L.append("## Estado de la especificacion (generado automaticamente)")
    L.append("")
    L.append(f"Feature activa: `{rel}`  ")
    L.append(f"Ultima actualizacion: {ahora}")
    L.append("")

    # --- Artefactos presentes ---
    L.append("### Artefactos")
    L.append("")
    L.append("| Archivo | Rol | Estado |")
    L.append("|---|---|---|")
    textos: dict[str, str] = {}
    for nombre, rol in ARTIFACTS:
        p = spec_dir / nombre
        if p.exists():
            textos[nombre] = leer(p)
            fecha = datetime.fromtimestamp(p.stat().st_mtime).strftime("%Y-%m-%d")
            L.append(f"| `{nombre}` | {rol} | presente ({fecha}) |")
        else:
            L.append(f"| `{nombre}` | {rol} | **FALTA** |")
    # contratos
    contratos = spec_dir / "contracts"
    if contratos.is_dir():
        archivos = sorted(f.name for f in contratos.iterdir() if f.is_file())
        L.append(f"| `contracts/` | Contratos | {len(archivos)} archivo(s) |")
    else:
        L.append("| `contracts/` | Contratos | **FALTA** |")
    L.append("")

    todo = "\n".join(textos.values())

    # --- Inventario de identificadores ---
    conteos = contar_ids(todo)
    if conteos:
        partes = [f"{v} {k}" for k, v in sorted(conteos.items())]
        L.append(f"**Inventario de identificadores:** {', '.join(partes)}")
        L.append("")

    # --- Progreso de tareas ---
    if "tasks.md" in textos:
        hechas, total = progreso_tareas(textos["tasks.md"])
        if total:
            pct = round(hechas / total * 100)
            L.append(f"**Progreso de tareas:** {hechas}/{total} ({pct}%)")
            L.append("")

    # --- Decisiones de diseño ---
    dds = extraer_titulados(todo, "DD")
    if dds:
        L.append("### Decisiones de diseño (DD)")
        L.append("")
        for d in dds:
            L.append(f"- {d}")
        L.append("")

    # --- Investigacion / trade-offs ---
    rs = extraer_titulados(textos.get("research.md", ""), "R")
    if rs:
        L.append("### Research (R)")
        L.append("")
        for r in rs:
            L.append(f"- {r}")
        L.append("")

    # --- Cuestiones abiertas ---
    abiertas = extraer_seccion(
        todo,
        ["qué no resuelve", "que no resuelve", "decisiones abiertas",
         "open questions", "cuestiones abiertas", "riesgos abiertos"],
    )
    if abiertas:
        L.append("### Abierto / no resuelto")
        L.append("")
        for a in abiertas:
            L.append(f"- {a}")
        L.append("")

    L.append("### Reglas de trabajo")
    L.append("")
    L.append("- La especificacion manda sobre la implementacion.")
    L.append("- Una tarea, un diff, un commit. No avanzar a la siguiente sin cerrar.")
    L.append(f"- Fuente de verdad: los archivos en `{rel}/`, no este resumen.")
    L.append("- Este bloque es generado: editarlo a mano no tiene efecto.")
    L.append("")
    L.append(END)
    return "\n".join(L)


def aplicar(contenido: str, bloque: str) -> str:
    if BEGIN in contenido and END in contenido:
        ini = contenido.index(BEGIN)
        fin = contenido.index(END) + len(END)
        return contenido[:ini] + bloque + contenido[fin:]
    sep = "" if contenido.endswith("\n\n") else ("\n" if contenido.endswith("\n") else "\n\n")
    return contenido + sep + "\n" + bloque + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", default=None,
                    help="nombre de la carpeta en specs/ (default: la de numero mas alto)")
    ap.add_argument("--agent-file", default=None,
                    help="ruta al archivo de agente (default: autodetecta)")
    ap.add_argument("--check", action="store_true",
                    help="no escribe; sale con 1 si el archivo esta desactualizado")
    args = ap.parse_args()

    root = repo_root(Path.cwd())

    spec_dir = find_spec_dir(root, args.spec)
    if spec_dir is None:
        print(f"ERROR: no encontre carpeta de spec en {root / 'specs'}", file=sys.stderr)
        return 2

    if args.agent_file:
        agente = Path(args.agent_file)
    else:
        agente = find_agent_file(root)
        if agente is None:
            agente = root / "CLAUDE.md"
            agente.write_text("# Contexto del proyecto\n", encoding="utf-8")
            print(f"(creado {agente.name})")

    # aviso de convencion: AGENTS.md deberia ser el canonico
    presentes = [n for n in AGENT_FILES if (root / n).exists()]
    if len(presentes) > 1:
        reales = [n for n in presentes if not (root / n).is_symlink()]
        if len(reales) > 1:
            print(f"AVISO: hay {len(reales)} archivos de agente reales ({', '.join(reales)}). "
                  f"Conviene uno canonico y el resto symlink.", file=sys.stderr)

    bloque = construir_bloque(root, spec_dir)
    actual = leer(agente)
    nuevo = aplicar(actual, bloque)

    if args.check:
        if actual != nuevo:
            print(f"{agente.name} DESACTUALIZADO. Corré: python scripts/update_agent_context.py")
            return 1
        print(f"{agente.name} al dia.")
        return 0

    if actual == nuevo:
        print(f"{agente.name} ya estaba al dia ({spec_dir.name}).")
        return 0

    agente.write_text(nuevo, encoding="utf-8")
    print(f"{agente.name} actualizado desde {spec_dir.relative_to(root).as_posix()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())