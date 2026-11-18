"""Replace compiled classes in a JAR, preserving the other entry contents.

Usage: repack.py ORIGINAL_JAR CLASSES_DIR OUT_JAR
"""

from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from zipfile import ZipFile, ZipInfo


def top_level_class(name: str) -> str:
    return name.removesuffix(".class").split("$", 1)[0]


def repack(original_jar: Path, classes_dir: Path, out_jar: Path) -> None:
    classes = sorted(classes_dir.rglob("*.class"))
    recompiled = {top_level_class(path.relative_to(classes_dir).as_posix()) for path in classes}
    if not recompiled:
        raise ValueError(f"No compiled classes in {classes_dir}")

    # ZipInfo defaults to a fixed timestamp and stores entries without compression.
    # Preserve the input entry order so the manifest stays in its original position.
    with TemporaryDirectory(dir=out_jar.parent) as work:
        replacement = Path(work) / out_jar.name
        with ZipFile(original_jar) as original, ZipFile(replacement, "w") as output:
            for entry in original.infolist():
                if entry.filename.endswith(".class") and top_level_class(entry.filename) in recompiled:
                    continue
                output.writestr(ZipInfo(entry.filename), original.read(entry))

            for path in classes:
                name = path.relative_to(classes_dir).as_posix()
                output.writestr(ZipInfo(name), path.read_bytes())

        replacement.replace(out_jar)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    repack(*(Path(argument) for argument in sys.argv[1:]))
