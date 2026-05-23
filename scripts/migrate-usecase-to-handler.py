#!/usr/bin/env python3
"""Migrate Bone *UseCase wrappers to direct *Handler injection (DDD v4)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

HANDLER_FIELD_RE = re.compile(
    r"private\s+final\s+(\w+Handler)\s+(\w+)\s*;"
)
USECASE_CLASS_RE = re.compile(r"public\s+class\s+(\w+UseCase)\b")
IMPORT_USECASE_RE = re.compile(
    r"import\s+([\w.]+\.)?application\.usecase(?:\.\w+)*\.(\w+UseCase)\s*;"
)
WILDCARD_USECASE_IMPORT_RE = re.compile(
    r"import\s+([\w.]+\.)application\.usecase\.\*\s*;"
)


def find_java_files() -> list[Path]:
    skip = {"target", "archunit_store", ".git", "node_modules"}
    files: list[Path] = []
    for base in [
        ROOT / "bone-framework",
        ROOT / "bone-platform",
        ROOT / "bone-engine",
        ROOT / "bone-blueprint",
    ]:
        if not base.exists():
            continue
        for p in base.rglob("*.java"):
            if any(part in skip for part in p.parts):
                continue
            files.append(p)
    return files


def parse_usecase_mappings() -> dict[str, dict[str, str]]:
    mappings: dict[str, dict[str, str]] = {}
    for p in find_java_files():
        if not p.name.endswith("UseCase.java"):
            continue
        if "bone-core" in str(p):
            continue
        text = p.read_text(encoding="utf-8")
        m_class = USECASE_CLASS_RE.search(text)
        if not m_class:
            continue
        use_case = m_class.group(1)
        m_handler = HANDLER_FIELD_RE.search(text)
        if not m_handler:
            continue
        handler_class = m_handler.group(1)
        handler_field = m_handler.group(2)
        # infer handler FQCN from imports in use case file
        handler_import = None
        for line in text.splitlines():
            if f"import " in line and handler_class in line:
                handler_import = line.strip().removeprefix("import ").removesuffix(";")
                break
        if not handler_import:
            # fallback: same module, guess package from path
            pkg = ".".join(p.relative_to(ROOT).parts[:-1]).replace("/", ".")
            if "command" in str(p) or "usecase" in str(p):
                handler_import = pkg.replace(".usecase.", ".command.handler.").replace(
                    ".usecase.standard", ".command.handler"
                )
            handler_import = f"{handler_import}.{handler_class}" if handler_import else handler_class
        use_case_field = use_case[0].lower() + use_case[1:]
        if use_case_field.endswith("UseCase"):
            use_case_field = use_case_field[:-7] + "UseCase"  # keep full camelCase
        mappings[use_case] = {
            "handler_class": handler_class,
            "handler_import": handler_import,
            "handler_field": handler_field,
            "use_case_field": use_case_field,
        }
    return mappings


def replace_capability_imports(text: str) -> str:
    text = text.replace(
        "import com.bone.core.usecase.Capability;",
        "import com.bone.core.capability.Capability;",
    )
    text = text.replace(
        "import com.bone.core.usecase.HandlerRegistry;",
        "import com.bone.core.capability.HandlerRegistry;",
    )
    return text


def migrate_file(path: Path, mappings: dict[str, dict[str, str]]) -> bool:
    if path.name.endswith("UseCase.java"):
        return False
    original = path.read_text(encoding="utf-8")
    text = replace_capability_imports(original)

    for use_case, info in mappings.items():
        handler_class = info["handler_class"]
        handler_import = info["handler_import"]
        use_case_field = info["use_case_field"]
        handler_field = info["handler_field"]

        # import
        use_case_import_pat = re.compile(
            rf"import\s+[\w.]+\.{re.escape(use_case)}\s*;\n?"
        )
        if use_case_import_pat.search(text):
            text = use_case_import_pat.sub(f"import {handler_import};\n", text)

        # field type
        text = re.sub(
            rf"\b{re.escape(use_case)}\s+{re.escape(use_case_field)}\b",
            f"{handler_class} {handler_field}",
            text,
        )
        # method call
        text = re.sub(
            rf"\b{re.escape(use_case_field)}\.execute\s*\(",
            f"{handler_field}.handle(",
            text,
        )

    # wildcard usecase imports — expand known use cases used in file
    for m in WILDCARD_USECASE_IMPORT_RE.finditer(text):
        pkg_prefix = m.group(1)
        for use_case, info in mappings.items():
            if f"{use_case}" in text:
                imp = f"import {info['handler_import']};\n"
                if imp not in text:
                    text = text.replace(m.group(0), imp, 1)
        text = text.replace(m.group(0), "")

    # remove bone-core usecase imports
    text = re.sub(r"import\s+com\.bone\.core\.usecase\.\w+\s*;\n?", "", text)
    text = re.sub(
        r"import\s+com\.bone\.studio\.generator\.application\.usecase\.\w+\s*;\n?",
        "",
        text,
    )
    text = re.sub(
        r"import\s+com\.bone\.studio\.generator\.application\.usecase\.UseCaseExecutor\s*;\n?",
        "",
        text,
    )

    if text != original:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def delete_usecase_files() -> int:
    deleted = 0
    for p in find_java_files():
        if not p.name.endswith("UseCase.java"):
            continue
        if "bone-core" in str(p) and "usecase" in str(p):
            continue
        p.unlink()
        deleted += 1
        print(f"deleted {p.relative_to(ROOT)}")
    return deleted


def delete_core_usecase_package() -> None:
    pkg = ROOT / "bone-framework/bone-core/src/main/java/com/bone/core/usecase"
    if pkg.exists():
        for f in pkg.glob("*.java"):
            f.unlink()
            print(f"deleted {f.relative_to(ROOT)}")
        try:
            pkg.rmdir()
        except OSError:
            pass


def delete_studio_usecase_spi() -> None:
    for name in ("UseCase.java", "UseCaseExecutor.java"):
        p = (
            ROOT
            / "bone-engine/studio-generator/src/main/java/com/bone/studio/generator/application/usecase"
            / name
        )
        if p.exists():
            p.unlink()
            print(f"deleted {p.relative_to(ROOT)}")


def main() -> int:
    mappings = parse_usecase_mappings()
    print(f"parsed {len(mappings)} UseCase -> Handler mappings")
    changed = 0
    for p in find_java_files():
        if migrate_file(p, mappings):
            changed += 1
    print(f"updated {changed} files")
    n = delete_usecase_files()
    print(f"deleted {n} UseCase files")
    delete_core_usecase_package()
    delete_studio_usecase_spi()
    return 0


if __name__ == "__main__":
    sys.exit(main())
