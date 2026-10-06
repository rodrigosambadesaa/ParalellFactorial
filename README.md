# Parallel Factorial

Java 17/Eclipse project with two complementary high-performance implementations:

- exact integer factorial using parallel Prime Swing;
- certified arbitrary-precision factorial for decimal and complex arguments.

## Exact integer factorial

```java
BigInteger result = ParalellFactorial.factorial(n);
```

For practical `int`-sized inputs the implementation uses an odd-only prime
sieve and the Prime Swing recurrence:

```text
odd(n!) = odd(floor(n/2)!)² × swing(n)
n!      = odd(n!) × 2^(n - bitCount(n))
```

Swing factors and final products are multiplied through balanced
`ForkJoinPool` trees. This avoids the increasingly unbalanced `BigInteger ×
small integer` sequence of the elementary loop and lets `BigInteger` use its
fast multiplication algorithms. Inputs beyond `Integer.MAX_VALUE` retain an
exact `BigInteger` divide-and-conquer fallback, although such results are far
beyond ordinary machine resources.

The implementation follows the Prime Swing formulation documented by Peter
Luschny and the factorial algorithm notes in GNU MP; it is independently
implemented for this project.

Run:

```text
programas.ParalellFactorial
```

## Certified complex factorial

The analytic continuation is:

```text
z! = Gamma(z + 1)
```

The Java API keeps every decimal input digit as text and asks FLINT/Arb to
evaluate Gamma with arbitrary-precision complex ball arithmetic:

```java
ComplexFactorial.CertifiedResult result =
        ComplexFactorial.factorial("1.1i", 100_000L);

String real = result.real();
String imaginary = result.imaginary();
String checksum = result.sha256();
```

The worker starts with 64 guard digits and automatically doubles them if the
rigorous interval does not yet determine a unique requested rounding. Java
accepts the answer only when both endpoints round identically and the SHA-256
digest also matches.

The components are returned as decimal strings, avoiding the fixed `int`
precision ceiling of `MathContext`. The request is a positive `long`; there is
no small application-configured ceiling, though every real calculation is
bounded by time, memory and the underlying JVM/Python/FLINT data structures.

Command-line example:

```text
programas.ComplexFactorial 1.1i 100000 factorial-1.1i-100000.txt
```

Accepted inputs include `3.5`, `2i`, `-i`, `3.5-2.25i` and scientific
notation. Both `1.1i` and `1,1i` are accepted. Negative integers are rejected
because Gamma has poles there.

## Requirements and Eclipse

- Java 17 or newer
- Python 3
- Eclipse JDT and Maven Integration (`m2e`)
- Maven outside Eclipse

Install the numerical backend once:

```bash
python3 -m pip install -r requirements.txt
```

Import with **File → Import → Maven → Existing Maven Projects**, select this
repository and run either main class as a Java application. Set `PYTHON` when
the desired executable is not named `python3` or `python`; set
`FACTORIAL_THREADS` to override Arb's default of at most eight threads.

## Verification

`mvn verify` checks Prime Swing against an independent sequential reference
for every value from 0 through 2,000, validates `10000!`, exercises malformed
input handling and tests decimal-complex parsing. GitHub Actions additionally
runs a certified complex smoke calculation.
