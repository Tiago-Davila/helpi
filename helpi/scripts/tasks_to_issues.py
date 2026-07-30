#!/usr/bin/env python3
"""
tasks_to_issues.py — Convierte tasks.md en issues de GitHub (via gh CLI).

Crea/actualiza labels, crea un issue por tarea, evita duplicados y guarda un
mapa tarea -> issue para trazabilidad.

Uso (correr desde la raiz del repo, donde esta specs/):
    P=.specify/scripts/powershell/tasks_to_issues.py
    python $P --validate-only    # solo chequear el parseo, no toca nada
    python $P --dry-run          # muestra issues y labels que crearia
    python $P                    # crear de verdad
    python $P --only T001 T002
    python $P --phase 3

Requiere: gh CLI autenticado (gh auth login).
Sin dependencias de Python externas.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------------------
# REGEX — el corazon del script. Comentado porque es donde todo se rompe.
# ---------------------------------------------------------------------------

# Linea de tarea. Claves del diseño:
#  - El ID esta ANCLADO al inicio de linea, tras bullet y checkbox.
#  - EL BULLET Y EL CHECKBOX SON OBLIGATORIOS. Anclar el ID al inicio de linea
#    NO alcanza, y esto costo 10 tareas fantasma: tasks.md envuelve parrafos a
#    ~100 chars, asi que una linea de PROSA puede empezar con el ID
#    ("T044 (fijar el baseline nuevo) es el nudo del proyecto: ...") y las listas
#    de grupos paralelos empiezan con bullet + RANGO
#    ("- T002-T006 (setup por repositorio)"). Ambas creaban tareas falsas que
#    encima ganaban el dedup por aparecer antes que la tarea real, borrandola:
#    T044 terminaba siendo un issue titulado ", porque el Principio V lo exige."
#    El formato de Spec Kit exige "- [ ] T### ...", asi que el checkbox es el
#    unico discriminador confiable entre tarea y mencion.
#  - No se capturan marcadores acá: se parsean después, uno por uno, para no
#    tragarse links markdown [texto](url) ni rangos tipo [0,1].
TASK_RE = re.compile(
    r"""^
    [ \t]{0,7}                    # sangría tolerada
    (?:[-*+][ \t]+)               # bullet OBLIGATORIO
    \[(?P<check>[ xX])\][ \t]*    # checkbox OBLIGATORIO (ver comentario arriba)
    (?:\*\*|__)?                  # apertura de negrita opcional
    (?P<id>T\d{1,4})              # ID de tarea  <-- ANCLADO
    (?:\*\*|__)?                  # cierre de negrita opcional
    (?![\w-])                     # el ID no sigue con letra/guion (evita T001a, T0011)
    [ \t]*[:.\u2013\u2014-]?[ \t]*    # separador opcional (: . – — -)
    (?P<rest>.*)$
    """,
    re.VERBOSE,
)

# Marcadores permitidos al PRINCIPIO del resto. Lista cerrada a propósito:
# cualquier corchete que no matchee corta el parseo y queda como texto.
# La negrita alrededor del marcador es OPCIONAL y hay que consumirla: tasks.md
# escribe el bloqueo resaltado -> "[IMPL] **[BLOQUEADA: D5 ...]** Fijar los...".
# Sin tolerar los "**", el loop cortaba en ese marcador y las 6 tareas bloqueadas
# perdian la label "blocked" y se llevaban "[BLOQUEADA: ...]" dentro del titulo.
MARKER_RE = re.compile(
    r"""^
    (?:\*\*|__)?                            # negrita de apertura opcional
    \[
    (?P<marker>
        P                                   # paralelizable
      | IMPL | EXP                          # tipo de tarea
      | BLOQUEADA[^\]]*                     # BLOQUEADA: <motivo>
      | BLOCKED[^\]]*
      | TEST | DOCS
    )
    \]
    (?:\*\*|__)?                            # negrita de cierre opcional
    [ \t]*
    """,
    re.VERBOSE | re.IGNORECASE,
)

# Encabezado de fase: "## Fase 3: ..." / "## Phase 3 — ..." / "## 3. ..."
PHASE_RE = re.compile(
    r"^#{1,4}[ \t]*(?:(?:fase|phase)[ \t]*)?(?P<num>\d{1,2})[ \t]*[:.\u2013\u2014-]?[ \t]*(?P<title>.+?)[ \t]*$",
    re.IGNORECASE,
)
# Cualquier encabezado (para cortar la captura de líneas de continuación)
ANY_HEADING_RE = re.compile(r"^#{1,6}[ \t]+\S")

# Cerca de código: hay que ignorar todo lo que esté dentro, o los ejemplos
# de tareas dentro de bloques ``` generan issues fantasma.
FENCE_RE = re.compile(r"^[ \t]*(?:```|~~~)")

# Referencias a requisitos / decisiones.
# La regla G del proyecto acepta requisito (FR/NFR/SC) O decision de diseño, y las
# decisiones se citan de mas formas que IDs con guion: "Principio XI", "deuda XII.1",
# "regla D", "AD-05". Sin estas alternativas el validador marcaba 34 tareas como
# "sin referencia" teniendo traza perfectamente valida.
# Sin IGNORECASE a proposito: con el flag, [IVXL]+ matchea dentro de palabras comunes.
REF_RE = re.compile(
    r"\b(?:FR|NFR|SC|DD|R|AD)-\d{1,4}\b"          # FR-001, NFR-014, R-009, AD-05
    r"|\bUS\d{1,3}\b"                              # US1
    r"|[Pp]rincipios?[ \t]+[IVXL]+\b"              # Principio XI, Principios V y VI
    r"|[Dd]euda[ \t]+[IVXL]+(?:\.\d+)?\b"          # deuda XII.1
    r"|[Rr]eglas?[ \t]+[A-G]\b"                    # regla D
)

# Segmento de traza del formato de este tasks.md:
#   "↳ Traza: <refs> · Dep: <ids> · Repo: <repo>"
# Se captura entero porque muchas trazas apuntan a secciones de documento
# ("plan §Fase B", "constitution §Puertas de CI", "edge case ...") que no son
# un ID y que ningun regex de IDs va a reconocer, pero SI son trazabilidad valida.
TRAZA_RE = re.compile(r"(?:↳[ \t]*)?traza[ \t]*[:–—-][ \t]*(?P<txt>[^·\n]+)", re.IGNORECASE)

# Dependencias declaradas. Formas soportadas:
#   "Dep: T001, T002"  <- la que usa este tasks.md, y la que faltaba: sin ella
#                         NO se extraia ninguna dependencia y la validacion de
#                         "depende de X que no existe" nunca se ejecutaba.
#   "Depende de: T001" / "Dependencias: T003" / "depends on T003" / "blocked by T003"
# El corte de ids es por "\u00b7" o fin de linea, no por ".": el formato separa campos
# con "\u00b7" ("Dep: T074, T062 \u00b7 Repo: ml") y cortar en "." partia IDs de secciones.
DEP_LINE_RE = re.compile(
    r"(?:depende(?:n)?[ \t]+de|dependencias?|deps?\b|depends?[ \t]+on|blocked[ \t]+by)"
    r"[ \t]*[:\u2013\u2014-]?[ \t]*(?P<ids>[^\u00b7\n]*)",
    re.IGNORECASE,
)
TID_RE = re.compile(r"\bT\d{1,4}\b")

# Rutas de archivo entre backticks
PATH_RE = re.compile(r"`([^`\n]+?)`")

# "Checkpoint" al final de fase: no es tarea
CHECKPOINT_RE = re.compile(r"^[ \t]*[-*+]?[ \t]*\**\s*(?:checkpoint|punto de control)\b", re.IGNORECASE)


# ---------------------------------------------------------------------------
# Labels
# ---------------------------------------------------------------------------

LABELS: dict[str, tuple[str, str]] = {
    # nombre: (color hex sin #, descripcion)
    "ml":            ("5319e7", "Pipeline de datos, entrenamiento y evaluacion"),
    "backend":       ("1d76db", "Servicios del lado servidor"),
    "frontend":      ("0e8a16", "Cliente web y UI"),
    "specs":         ("bfd4f2", "Documentos de especificacion"),
    "infra":         ("444444", "Setup, CI, tooling"),
    "contract":      ("b60205", "Contrato de datos entre componentes"),
    "model":         ("8b5cf6", "Modelo, metricas y baseline"),
    "security":      ("d93f0b", "Privacidad, consentimiento y datos sensibles"),
    "accessibility": ("fbca04", "Accesibilidad y validacion con usuarias"),
    "test":          ("c2e0c6", "Tests y verificacion"),
    "experiment":    ("d4c5f9", "Tarea experimental: puede fallar"),
    "blocked":       ("e11d21", "Bloqueada por una decision abierta"),
    "parallel":      ("ededed", "Paralelizable"),
    "docs":          ("cfd3d7", "Documentacion"),
}

# Palabras clave -> label temático. Se busca en titulo + cuerpo + rutas.
KEYWORDS: dict[str, tuple[str, ...]] = {
    "contract":      ("contrato", "contract", "201 coordenad", "keypoint", "equivalencia",
                      "normalizacion temporal", "normalización temporal", "linspace"),
    "model":         ("modelo", "entrenar", "entrenamiento", "baseline", "accuracy",
                      "matriz de confusion", "matriz de confusión", "split por sujeto",
                      "umbral", "confianza", "ablacion", "ablación", "dataset"),
    "security":      ("privacidad", "privac", "consentimiento", "seguridad", "security",
                      "video crudo", "produccion no incluy", "producción no incluy"),
    "accessibility": ("accesibilidad", "sorda", "sordas", "interprete", "intérprete",
                      "tts", "voz", "web speech", "usabilidad", "encuadre"),
    "test":          ("test", "prueba", "verificar", "verificacion", "verificación", "ci"),
    "docs":          ("documentar", "documentacion", "documentación", "readme", "quickstart"),
    "infra":         ("setup", "inicializar", "ci/cd", "pipeline de ci", "tooling", "hook"),
}

def _kw_regex(palabras: tuple[str, ...]) -> re.Pattern:
    """Compila keywords con límite de palabra.
    Crítico: sin \b, una keyword corta como "ci" matchea dentro de
    "dependenCIas" o "inferenCIa" y ensucia todas las labels.
    Keywords de <=3 chars llevan límite en ambos extremos; las más largas
    solo al inicio, para tolerar plurales y flexiones."""
    partes = []
    for w in palabras:
        esc = re.escape(w)
        partes.append(rf"\b{esc}\b" if len(w) <= 3 else rf"\b{esc}")
    return re.compile("|".join(partes), re.IGNORECASE)


KEYWORDS_RE: dict[str, re.Pattern] = {k: _kw_regex(v) for k, v in KEYWORDS.items()}

# Rutas -> label de area
PATH_AREA = (
    ("frontend/", "frontend"),
    ("backend/", "backend"),
    ("ml/", "ml"),
    ("specs/", "specs"),
    ("scripts/", "infra"),
    (".github/", "infra"),
)


@dataclass
class Task:
    tid: str
    title: str
    phase_num: int | None = None
    phase_title: str = ""
    done: bool = False
    parallel: bool = False
    kind: str = ""            # IMPL / EXP / TEST / DOCS
    blocked_reason: str = ""
    files: list[str] = field(default_factory=list)
    refs: list[str] = field(default_factory=list)
    deps: list[str] = field(default_factory=list)
    trace_text: str = ""      # segmento "Traza:" crudo, para refs no-ID (§secciones)
    body_lines: list[str] = field(default_factory=list)
    line_no: int = 0

    @property
    def num(self) -> int:
        return int(self.tid[1:])

    def labels(self) -> list[str]:
        out: set[str] = set()

        # area por rutas
        blob_paths = " ".join(self.files).lower()
        for prefijo, label in PATH_AREA:
            if prefijo in blob_paths:
                out.add(label)

        # Solo título + rutas: la prosa del cuerpo genera falsos positivos.
        texto = " ".join([self.title, *self.files])

        # area por fase / texto si las rutas no alcanzaron
        if not out & {"ml", "backend", "frontend", "specs", "infra"}:
            fase = self.phase_title.lower()
            for palabra, label in (("frontend", "frontend"), ("cliente", "frontend"),
                                   ("backend", "backend"), ("servicio", "backend"),
                                   ("pipeline", "ml"), ("dataset", "ml"),
                                   ("evaluaci", "ml"), ("modelo", "ml")):
                if re.search(rf"\b{palabra}", fase, re.I) or re.search(rf"\b{palabra}", texto, re.I):
                    out.add(label)
                    break

        # tematicos
        for label, rx in KEYWORDS_RE.items():
            if rx.search(texto):
                out.add(label)

        # tipo y estado
        if self.kind.upper() == "EXP":
            out.add("experiment")
        if self.kind.upper() == "TEST":
            out.add("test")
        if self.kind.upper() == "DOCS":
            out.add("docs")
        if self.blocked_reason:
            out.add("blocked")
        if self.parallel:
            out.add("parallel")
        if self.phase_num is not None:
            out.add(f"phase-{self.phase_num:02d}")

        return sorted(l for l in out if l)

    def issue_title(self) -> str:
        return f"{self.tid}: {self.title}".strip()

    def issue_body(self, tasks_rel: str) -> str:
        L: list[str] = []
        L.append(f"**Tarea `{self.tid}`**")
        if self.phase_num is not None:
            L.append(f"Fase {self.phase_num} — {self.phase_title}")
        L.append("")
        if self.kind:
            L.append(f"- Tipo: `{self.kind.upper()}`")
        if self.parallel:
            L.append("- Paralelizable: sí")
        if self.blocked_reason:
            L.append(f"- ⚠️ **BLOQUEADA**: {self.blocked_reason}")
        if self.files:
            L.append("- Archivos: " + ", ".join(f"`{f}`" for f in self.files))
        if self.refs:
            L.append("- Traza a: " + ", ".join(self.refs))
        elif self.trace_text:
            L.append(f"- Traza a: {self.trace_text}")
        else:
            L.append("- Traza a: **(sin referencia — revisar)**")
        if self.deps:
            L.append("- Depende de: " + ", ".join(f"`{d}`" for d in self.deps))
        L.append("")
        if self.body_lines:
            L.append("### Detalle")
            L.append("")
            for ln in self.body_lines:
                L.append(ln)
            L.append("")
        L.append("---")
        L.append(f"Generado desde `{tasks_rel}` (línea {self.line_no}). "
                 f"La fuente de verdad es la spec, no este issue.")
        L.append("")
        L.append("Regla: una tarea, un diff, un commit.")
        return "\n".join(L)


# ---------------------------------------------------------------------------
# Parseo
# ---------------------------------------------------------------------------

def parse_tasks(texto: str) -> tuple[list[Task], list[str]]:
    """Devuelve (tareas, avisos)."""
    tareas: list[Task] = []
    avisos: list[str] = []
    en_fence = False
    fase_num: int | None = None
    fase_titulo = ""
    actual: Task | None = None
    sangria_tarea = 0

    lineas = texto.splitlines()
    for i, raw in enumerate(lineas, start=1):
        # 1) bloques de codigo: ignorar TODO adentro
        if FENCE_RE.match(raw):
            en_fence = not en_fence
            continue
        if en_fence:
            continue

        # 2) encabezados: cortan la tarea actual y pueden abrir fase
        if ANY_HEADING_RE.match(raw):
            actual = None
            m = PHASE_RE.match(raw)
            if m:
                fase_num = int(m.group("num"))
                fase_titulo = re.sub(r"[*`#]", "", m.group("title")).strip()
            continue

        # 3) checkpoints no son tareas
        if CHECKPOINT_RE.match(raw):
            actual = None
            continue

        # 4) intento de tarea
        m = TASK_RE.match(raw)
        if m:
            resto = m.group("rest")

            # marcadores: se consumen SOLO si estan en la lista blanca
            parallel = False
            kind = ""
            bloqueo = ""
            while True:
                mm = MARKER_RE.match(resto)
                if not mm:
                    break
                marker = mm.group("marker")
                up = marker.upper()
                if up == "P":
                    parallel = True
                elif up in ("IMPL", "EXP", "TEST", "DOCS"):
                    kind = up
                elif up.startswith(("BLOQUEADA", "BLOCKED")):
                    bloqueo = re.sub(r"^(?:BLOQUEADA|BLOCKED)[ \t]*[:\u2013\u2014-]?[ \t]*",
                                     "", marker, flags=re.IGNORECASE).strip() or "sin motivo"
                resto = resto[mm.end():]

            titulo = re.sub(r"\s+", " ", resto).strip(" -–—:")
            titulo = re.sub(r"[*_]{2,}", "", titulo)

            if not titulo:
                avisos.append(f"L{i}: {m.group('id')} sin título, se omite")
                actual = None
                continue

            t = Task(
                tid=m.group("id").upper(),
                title=titulo,
                phase_num=fase_num,
                phase_title=fase_titulo,
                done=(m.group("check") or " ").lower() == "x",
                parallel=parallel,
                kind=kind,
                blocked_reason=bloqueo,
                line_no=i,
            )
            t.files = [p.strip() for p in PATH_RE.findall(titulo)
                       if "/" in p or p.endswith((".py", ".ts", ".tsx", ".md", ".yaml", ".yml", ".json"))]
            t.refs = list(dict.fromkeys(REF_RE.findall(titulo)))

            dm = DEP_LINE_RE.search(titulo)
            if dm:
                t.deps = [d.upper() for d in TID_RE.findall(dm.group("ids"))]

            tareas.append(t)
            actual = t
            sangria_tarea = len(raw) - len(raw.lstrip())
            continue

        # 5) linea de continuacion de la tarea actual
        if actual is not None:
            if not raw.strip():
                continue
            sangria = len(raw) - len(raw.lstrip())
            if sangria <= sangria_tarea and re.match(r"^[ \t]*[-*+][ \t]", raw):
                # bullet al mismo nivel que no es tarea -> se corta
                actual = None
                continue
            limpio = raw.strip()
            actual.body_lines.append(limpio)
            actual.files += [p.strip() for p in PATH_RE.findall(limpio)
                             if "/" in p or p.endswith((".py", ".ts", ".tsx", ".md", ".yaml", ".yml", ".json"))]
            actual.refs += [r for r in REF_RE.findall(limpio)]
            tm = TRAZA_RE.search(limpio)
            if tm and not actual.trace_text:
                actual.trace_text = tm.group("txt").strip()
            dm = DEP_LINE_RE.search(limpio)
            if dm:
                actual.deps += [d.upper() for d in TID_RE.findall(dm.group("ids"))]

    # dedup y limpieza
    for t in tareas:
        t.files = list(dict.fromkeys(t.files))
        t.refs = list(dict.fromkeys(t.refs))
        t.deps = [d for d in dict.fromkeys(t.deps) if d != t.tid]

    # --- validaciones ---
    vistos: dict[str, Task] = {}
    for t in tareas:
        if t.tid in vistos:
            avisos.append(
                f"ID DUPLICADO {t.tid}: líneas {vistos[t.tid].line_no} y {t.line_no}")
        else:
            vistos[t.tid] = t

    if tareas:
        nums = sorted(t.num for t in vistos.values())
        faltantes = [n for n in range(nums[0], nums[-1] + 1) if n not in set(nums)]
        if faltantes:
            avisos.append("IDs faltantes en la secuencia: " +
                          ", ".join(f"T{n:03d}" for n in faltantes[:20]))

    ids = set(vistos)
    for t in vistos.values():
        for d in t.deps:
            if d not in ids:
                avisos.append(f"{t.tid} depende de {d}, que no existe")
        if not t.refs and not t.trace_text:
            avisos.append(f"{t.tid} sin referencia a requisito ni decisión (regla G)")
        if t.phase_num is None:
            avisos.append(f"{t.tid} fuera de toda fase")

    return list(vistos.values()), avisos


# ---------------------------------------------------------------------------
# gh CLI
# ---------------------------------------------------------------------------

def gh(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(["gh", *args], capture_output=True, text=True, check=check)


def gh_disponible() -> bool:
    try:
        gh("auth", "status")
        return True
    except FileNotFoundError:
        print("ERROR: no encontré 'gh'. Instalá GitHub CLI.", file=sys.stderr)
    except subprocess.CalledProcessError:
        print("ERROR: gh no está autenticado. Corré: gh auth login", file=sys.stderr)
    return False


def asegurar_labels(nombres: set[str], dry: bool) -> None:
    for nombre in sorted(nombres):
        color, desc = LABELS.get(nombre, ("ededed", ""))
        if nombre.startswith("phase-"):
            color, desc = "006b75", f"Fase {nombre.split('-')[1]}"
        if dry:
            print(f"  [dry] label {nombre} (#{color})")
            continue
        r = gh("label", "create", nombre, "--color", color,
               "--description", desc or nombre, "--force", check=False)
        if r.returncode != 0:
            print(f"  aviso: label {nombre}: {r.stderr.strip()[:120]}")


def issues_existentes() -> dict[str, int]:
    """Mapea T### -> numero de issue, mirando issues abiertos y cerrados."""
    r = gh("issue", "list", "--state", "all", "--limit", "1000",
           "--json", "number,title", check=False)
    if r.returncode != 0:
        return {}
    out: dict[str, int] = {}
    try:
        for it in json.loads(r.stdout or "[]"):
            m = re.match(r"^\s*(T\d{1,4})\b", it["title"])
            if m:
                out[m.group(1).upper()] = it["number"]
    except json.JSONDecodeError:
        pass
    return out


def crear_issue(t: Task, tasks_rel: str, dry: bool) -> int | None:
    cuerpo = t.issue_body(tasks_rel)
    labels = t.labels()
    if dry:
        print(f"  [dry] #{t.tid}  {t.issue_title()}")
        print(f"        labels: {', '.join(labels)}")
        return None
    with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False,
                                     encoding="utf-8") as f:
        f.write(cuerpo)
        ruta = f.name
    try:
        args = ["issue", "create", "--title", t.issue_title(), "--body-file", ruta]
        for l in labels:
            args += ["--label", l]
        r = gh(*args, check=False)
        if r.returncode != 0:
            print(f"  ERROR {t.tid}: {r.stderr.strip()[:200]}")
            return None
        m = re.search(r"/issues/(\d+)", r.stdout)
        num = int(m.group(1)) if m else None
        print(f"  creado {t.tid} -> issue #{num}")
        return num
    finally:
        Path(ruta).unlink(missing_ok=True)


# ---------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tasks", default=None, help="ruta a tasks.md (default: autodetecta en specs/)")
    ap.add_argument("--dry-run", action="store_true", help="no crea nada, solo muestra")
    ap.add_argument("--validate-only", action="store_true", help="solo parsea y valida")
    ap.add_argument("--only", nargs="*", default=None, help="IDs específicos (T001 T002)")
    ap.add_argument("--phase", type=int, default=None, help="solo una fase")
    ap.add_argument("--include-done", action="store_true", help="incluir tareas ya marcadas [x]")
    ap.add_argument("--map-out", default="tasks_issues_map.json")
    args = ap.parse_args()

    # localizar tasks.md
    if args.tasks:
        tasks_path = Path(args.tasks)
    else:
        cands = sorted(Path("specs").glob("*/tasks.md")) if Path("specs").is_dir() else []
        if not cands:
            print("ERROR: no encontré tasks.md. Usá --tasks.", file=sys.stderr)
            return 2
        tasks_path = cands[-1]
    if not tasks_path.exists():
        print(f"ERROR: no existe {tasks_path}", file=sys.stderr)
        return 2

    print(f"Parseando {tasks_path}")
    tareas, avisos = parse_tasks(tasks_path.read_text(encoding="utf-8"))
    print(f"Tareas detectadas: {len(tareas)}")

    if avisos:
        print(f"\n--- Avisos ({len(avisos)}) ---")
        for a in avisos:
            print(f"  ! {a}")
        bloqueantes = [a for a in avisos if "DUPLICADO" in a]
        if bloqueantes and not args.validate_only:
            print("\nHay IDs duplicados. Corregí tasks.md antes de crear issues.",
                  file=sys.stderr)
            return 1

    # filtros
    sel = tareas
    if args.only:
        pedidos = {s.upper() for s in args.only}
        sel = [t for t in sel if t.tid in pedidos]
    if args.phase is not None:
        sel = [t for t in sel if t.phase_num == args.phase]
    if not args.include_done:
        sel = [t for t in sel if not t.done]

    print(f"\nSeleccionadas: {len(sel)}")
    for t in sel[:200]:
        marca = " ".join(filter(None, [
            "[P]" if t.parallel else "",
            f"[{t.kind}]" if t.kind else "",
            "[BLOQUEADA]" if t.blocked_reason else "",
        ]))
        print(f"  {t.tid} F{t.phase_num or '-'} {marca} {t.title[:70]}")
        print(f"        labels: {', '.join(t.labels())}")

    if args.validate_only:
        return 0
    if not sel:
        print("Nada que crear.")
        return 0

    if not args.dry_run and not gh_disponible():
        return 2

    todos_labels = {l for t in sel for l in t.labels()}
    print(f"\n--- Labels ({len(todos_labels)}) ---")
    asegurar_labels(todos_labels, args.dry_run)

    existentes = {} if args.dry_run else issues_existentes()
    if existentes:
        print(f"\nIssues ya existentes: {len(existentes)}")

    print("\n--- Issues ---")
    mapa: dict[str, int] = dict(existentes)
    creados = saltados = 0
    tasks_rel = tasks_path.as_posix()
    for t in sorted(sel, key=lambda x: x.num):
        if t.tid in existentes:
            print(f"  salteado {t.tid} (ya existe: #{existentes[t.tid]})")
            saltados += 1
            continue
        num = crear_issue(t, tasks_rel, args.dry_run)
        if num:
            mapa[t.tid] = num
            creados += 1

    if not args.dry_run and mapa:
        Path(args.map_out).write_text(
            json.dumps(dict(sorted(mapa.items())), indent=2), encoding="utf-8")
        print(f"\nMapa tarea->issue en {args.map_out}")

    print(f"\nListo. Creados: {creados}. Salteados: {saltados}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())