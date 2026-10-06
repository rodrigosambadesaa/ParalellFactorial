#!/usr/bin/env python3
"""Fast exact integer factorial worker using FLINT/GMP."""

from __future__ import annotations

import hashlib
import os
import sys
import time
from pathlib import Path

try:
    from flint import ctx, fmpz
except ImportError as error:
    raise SystemExit(
        "python-flint is required. Install it with: "
        "python3 -m pip install -r requirements.txt"
    ) from error


def main() -> None:
    if len(sys.argv) != 4 or sys.argv[1] not in {"binary", "decimal"}:
        raise SystemExit("usage: worker (binary|decimal) N OUTPUT")

    mode, number_text, output_text = sys.argv[1:]
    number = int(number_text)
    if number < 0:
        raise ValueError("n must be non-negative")

    ctx.threads = max(1, int(os.environ.get(
        "FACTORIAL_THREADS", min(8, os.cpu_count() or 1))))

    calculation_start = time.perf_counter()
    value = fmpz.fac_ui(number)
    calculation_seconds = time.perf_counter() - calculation_start

    conversion_start = time.perf_counter()
    if mode == "binary":
        bit_length = value.bit_length()
        payload = int(value).to_bytes((bit_length + 7) // 8, "big")
        digits = 1 if number < 2 else 0
    else:
        text = str(value)
        payload = text.encode("ascii")
        bit_length = value.bit_length()
        digits = len(text)
    conversion_seconds = time.perf_counter() - conversion_start

    write_start = time.perf_counter()
    Path(output_text).write_bytes(payload)
    write_seconds = time.perf_counter() - write_start

    fields = {
        "DIGITS": digits,
        "BIT_LENGTH": bit_length,
        "CALCULATION_SECONDS": f"{calculation_seconds:.9f}",
        "CONVERSION_SECONDS": f"{conversion_seconds:.9f}",
        "WRITE_SECONDS": f"{write_seconds:.9f}",
        "SHA256": hashlib.sha256(payload).hexdigest(),
    }
    for name, field_value in fields.items():
        print(f"{name}\t{field_value}")


if __name__ == "__main__":
    main()
