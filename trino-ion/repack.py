"""Replace classes in a jar, reproducibly.

usage: repack.py ORIGINAL_JAR CLASSES_DIR OUT_JAR

OUT_JAR is ORIGINAL_JAR without the classes that CLASSES_DIR recompiles (for each recompiled
top-level class X: X.class and every X$*.class, so that no stale nested class survives),
followed by all classes in CLASSES_DIR.

The output depends only on the inputs, not on the local Python or zlib: the kept entries are
copied byte for byte, without decompressing them, and the new classes are stored uncompressed,
with the attributes and timestamp of the original jar's entries.
"""

from __future__ import annotations

import os
import pathlib
import re
import struct
import sys
import zlib

LOCAL_HEADER = struct.Struct("<IHHHHHIIIHH")
CENTRAL_HEADER = struct.Struct("<IHHHHHHIIIHHHHHII")
END_RECORD = struct.Struct("<IHHHHIIH")
LOCAL_SIGNATURE, CENTRAL_SIGNATURE, END_SIGNATURE = 0x04034B50, 0x02014B50, 0x06054B50
OFFSET_FIELD = slice(42, 46)  # where a central directory header holds its local header offset
FLAG_DATA_DESCRIPTOR, FLAG_UTF8 = 0x0008, 0x0800
VERSION_STORED = 10

Entry = tuple  # (name, central directory header, local header and data)


def read_entries(data: bytes) -> list[Entry]:
    end = data.rfind(struct.pack("<I", END_SIGNATURE))
    if end < 0:
        sys.exit("no end of central directory record")
    _, disk, _, count_on_disk, count, _, offset, _ = END_RECORD.unpack_from(data, end)
    if disk != 0 or count != count_on_disk or count == 0xFFFF or offset == 0xFFFFFFFF:
        sys.exit("split and zip64 archives are not supported")
    entries = []
    for _ in range(count):
        header = CENTRAL_HEADER.unpack_from(data, offset)
        if header[0] != CENTRAL_SIGNATURE:
            sys.exit(f"bad central directory header at {offset}")
        flags, compressed_size = header[3], header[8]
        name_length, extra_length, comment_length, local_offset = header[10], header[11], header[12], header[16]
        name = data[offset + 46 : offset + 46 + name_length].decode()
        if flags & FLAG_DATA_DESCRIPTOR:
            sys.exit(f"{name}: entries with a data descriptor are not supported")
        central = data[offset : offset + 46 + name_length + extra_length + comment_length]
        offset += len(central)
        local = LOCAL_HEADER.unpack_from(data, local_offset)
        if local[0] != LOCAL_SIGNATURE:
            sys.exit(f"{name}: bad local header at {local_offset}")
        local_end = local_offset + LOCAL_HEADER.size + local[9] + local[10] + compressed_size
        entries.append((name, central, data[local_offset:local_end]))
    return entries


def stored_entry(name: str, payload: bytes, template: bytes) -> Entry:
    """An uncompressed entry with the attributes and timestamp of the template central header."""
    t = CENTRAL_HEADER.unpack_from(template)
    made_by, flags, mod_time, mod_date, external_attributes = t[1], t[3] & FLAG_UTF8, t[5], t[6], t[15]
    encoded = name.encode()
    crc, size = zlib.crc32(payload), len(payload)
    local = LOCAL_HEADER.pack(
        LOCAL_SIGNATURE, VERSION_STORED, flags, 0, mod_time, mod_date, crc, size, size, len(encoded), 0
    )
    central = CENTRAL_HEADER.pack(
        CENTRAL_SIGNATURE, made_by, VERSION_STORED, flags, 0, mod_time, mod_date, crc, size, size,
        len(encoded), 0, 0, 0, 0, external_attributes, 0,
    )  # fmt: skip
    return name, central + encoded, local + encoded + payload


def top_level_class(name: str) -> str:
    """io/trino/Foo$Bar.class -> io/trino/Foo"""
    return re.sub(r"(\$.*)?\.class$", "", name)


def main(original_jar: str, classes_dir: str, out_jar: str) -> None:
    original = read_entries(pathlib.Path(original_jar).read_bytes())
    classes = {
        path.relative_to(classes_dir).as_posix(): path.read_bytes()
        for path in sorted(pathlib.Path(classes_dir).rglob("*.class"))
    }
    recompiled = {top_level_class(name) for name in classes}
    original_classes = {top_level_class(e[0]) for e in original if e[0].endswith(".class")}

    kept = [e for e in original if not (e[0].endswith(".class") and top_level_class(e[0]) in recompiled)]
    file_template = next(e[1] for e in original if not e[0].endswith("/"))
    directory_template = next((e[1] for e in original if e[0].endswith("/")), None)
    present = {e[0] for e in kept}
    added = []
    for name, payload in classes.items():
        parts = name.split("/")
        for i in range(1, len(parts)):  # directory entries for new packages, if the jar has any
            directory = "/".join(parts[:i]) + "/"
            if directory_template and directory not in present:
                added.append(stored_entry(directory, b"", directory_template))
                present.add(directory)
        added.append(stored_entry(name, payload, file_template))

    entries = kept + added
    if len(entries) >= 0xFFFF:
        sys.exit("too many entries for a non-zip64 archive")
    tmp = out_jar + ".tmp"
    with open(tmp, "wb") as out:
        central_directory = []
        for _, central, local in entries:
            offset = struct.pack("<I", out.tell())
            central_directory.append(central[: OFFSET_FIELD.start] + offset + central[OFFSET_FIELD.stop :])
            out.write(local)
        start = out.tell()
        out.write(b"".join(central_directory))
        out.write(END_RECORD.pack(END_SIGNATURE, 0, 0, len(entries), len(entries), out.tell() - start, start, 0))
    os.replace(tmp, out_jar)  # a new file, so a hard link to the original jar is never written through

    def short(names: set[str]) -> str:
        return ", ".join(sorted(n.rsplit("/", 1)[-1] for n in names)) or "-"

    print(
        f"{out_jar}: {len(original) - len(kept)} entries replaced by {len(added)}; "
        f"recompiled: {short(recompiled & original_classes)}; new: {short(recompiled - original_classes)}"
    )


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
