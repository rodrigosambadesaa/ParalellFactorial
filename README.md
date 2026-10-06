# Parallel Factorial

Java 17/Eclipse factorial project with native high-performance arithmetic,
pure-Java fallback and certified complex continuation.

## Exact integer factorial

```java
BigInteger result = ParalellFactorial.factorial(n);
```

For inputs of at least 100,000, the Java front end delegates to
`fmpz_fac_ui` from FLINT/GMP and imports the result as a binary magnitude.
This uses optimized native factorial and multiplication algorithms instead of
attempting to compete with Mathematica from `java.math.BigInteger`.

If Python or `python-flint` is unavailable, the same API falls back to an
independent parallel Prime Swing implementation with:

```text
odd(n!) = odd(floor(n/2)!)² × swing(n)
n!      = odd(n!) × 2^(n - bitCount(n))
```

Prime Swing is exact and substantially better than an elementary loop, but the
native backend is dramatically faster for giant inputs because Java
`BigInteger` multiplication and decimal conversion become the bottleneck.

### Direct decimal output

Do not call `BigInteger.toString()` for hundreds of millions of digits. The
optimized command calculates, converts and writes through FLINT/GMP:

```bash
java -cp target/classes programas.ParalellFactorial 30000000 factorial-30000000.txt
```

Interactive calculations of at least 1,000,000 are also redirected to a file
automatically instead of flooding the Eclipse console.

### Measured `30000000!` benchmark

Same 9-vCPU, 9.7-GiB environment, Java 17:

| Operation | Pure Java Prime Swing | FLINT/GMP backend |
| --- | ---: | ---: |
| Exact factorial | 500.31 s | 9.40 s |
| Decimal conversion | 1612.46 s | 48.83 s |
| Exact result size | 701,872,938 bits | 701,872,938 bits |
| Decimal digits | 211,284,808 | 211,284,808 |

The Java API verifies the native binary or decimal payload with SHA-256 before
accepting it.

## Certified complex factorial

The analytic continuation is:

```text
z! = Gamma(z + 1)
```

FLINT/Arb evaluates Gamma using arbitrary-precision complex ball arithmetic:

```java
ComplexFactorial.CertifiedResult result =
        ComplexFactorial.factorial("1.1i", 100_000L);

String real = result.real();
String imaginary = result.imaginary();
String checksum = result.sha256();
```

Every decimal input is converted to an exact rational without passing through
`double`. The worker starts with 64 guard digits and doubles them adaptively
until both endpoints of each rigorous interval round to the same requested
decimal result. Java then verifies the SHA-256 digest.

The decimal components are strings, avoiding the fixed `int` precision ceiling
of `MathContext`. The precision request is a positive `long`; practical limits
remain memory, execution time and the underlying JVM/Python/FLINT structures.

```bash
java -cp target/classes programas.ComplexFactorial \
  1.1i 100000 factorial-1.1i-100000.txt
```

Both decimal point and comma are accepted. Negative integers are rejected
because Gamma has poles there.

## Requirements and Eclipse

- Java 17 or newer
- Python 3
- Eclipse JDT and Maven Integration (`m2e`)
- Maven outside Eclipse

Install FLINT/GMP/Arb through the pinned wheel:

```bash
python3 -m pip install -r requirements.txt
```

Import with **File → Import → Maven → Existing Maven Projects**. Set `PYTHON`
when the desired executable is not named `python3` or `python`, and optionally
set `FACTORIAL_THREADS` to override the default of at most eight threads.

## Verification

`mvn verify` compares Prime Swing with an independent sequential implementation
from 0 through 2,000, validates `10000!`, tests native binary and decimal
transfer when FLINT is installed, checks malformed inputs and exercises the
complex parser. GitHub Actions installs the pinned backend and runs both exact
and certified-complex smoke calculations.
