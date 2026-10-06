#!/usr/bin/env python3
"""Certified arbitrary-precision complex factorial using FLINT/Arb."""

from __future__ import annotations

import hashlib
import os
import sys
import time
from decimal import Decimal

try:
    from flint import acb, arb, ctx, fmpq
except ImportError as error:
    raise SystemExit(
        "python-flint is required. Install it with: "
        "python3 -m pip install -r requirements.txt"
    ) from error


INITIAL_GUARD_DIGITS = 64
MAX_ATTEMPTS = 6


def decimal_rational(text: str) -> fmpq:
    """Convert a finite decimal string to an exact rational number."""
    value = Decimal(text)
    if not value.is_finite():
        raise ValueError("NaN and infinity are not valid inputs")

    sign, digits, exponent = value.as_tuple()
    numerator = int("".join(str(digit) for digit in digits) or "0")
    if sign:
        numerator = -numerator
    if exponent >= 0:
        return fmpq(numerator * (10 ** exponent), 1)
    return fmpq(numerator, 10 ** (-exponent))


def stable_decimal(ball: arb, digits: int) -> str:
    """Return the unique significant-digit rounding of an Arb interval."""
    lower = ball.lower().str(digits, radius=False)
    upper = ball.upper().str(digits, radius=False)
    if lower != upper:
        raise ArithmeticError("interval endpoints do not round identically")

    value = Decimal(lower)
    if not value.is_finite():
        raise ArithmeticError("factorial result is not finite")
    plain = format(value, "f")
    return "0" if value.is_zero() else plain


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("usage: worker REAL IMAGINARY PRECISION_DIGITS")

    if hasattr(sys, "set_int_max_str_digits"):
        sys.set_int_max_str_digits(0)

    real_text, imaginary_text, precision_text = sys.argv[1:]
    precision_digits = int(precision_text)
    if precision_digits <= 0:
        raise ValueError("precision must be positive")

    real_decimal = Decimal(real_text)
    imaginary_decimal = Decimal(imaginary_text)
    if (imaginary_decimal.is_zero()
            and real_decimal < 0
            and real_decimal == real_decimal.to_integral_value()):
        raise ValueError("factorial has a pole at negative integers")

    real = decimal_rational(real_text)
    imaginary = decimal_rational(imaginary_text)
    default_threads = max(1, min(8, os.cpu_count() or 1))
    ctx.threads = max(1, int(os.environ.get(
        "FACTORIAL_THREADS", default_threads)))

    started = time.perf_counter()
    guard_digits = INITIAL_GUARD_DIGITS
    last_error: ArithmeticError | None = None

    for _ in range(MAX_ATTEMPTS):
        working_digits = precision_digits + guard_digits
        ctx.dps = working_digits
        value = acb(arb(real) + 1, arb(imaginary)).gamma()
        try:
            real_result = stable_decimal(value.real, precision_digits)
            imaginary_result = stable_decimal(value.imag, precision_digits)
            break
        except ArithmeticError as error:
            last_error = error
            guard_digits *= 2
    else:
        raise ArithmeticError(
            "could not certify the requested rounding after adaptive retries"
        ) from last_error

    elapsed = time.perf_counter() - started
    canonical = f"{real_result}\n{imaginary_result}\n".encode("ascii")
    fields = {
        "CERTIFIED": "true",
        "PRECISION_DIGITS": precision_digits,
        "WORKING_DIGITS": working_digits,
        "REAL_ACCURACY_BITS": value.real.rel_accuracy_bits(),
        "IMAGINARY_ACCURACY_BITS": value.imag.rel_accuracy_bits(),
        "TIME_SECONDS": f"{elapsed:.9f}",
        "SHA256": hashlib.sha256(canonical).hexdigest(),
        "REAL": real_result,
        "IMAGINARY": imaginary_result,
    }
    for name, field_value in fields.items():
        print(f"{name}\t{field_value}")


if __name__ == "__main__":
    main()
