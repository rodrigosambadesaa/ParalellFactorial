# Parallel Factorial

Java 17 calculator providing two complementary factorial implementations:

- an exact `BigInteger` implementation for non-negative integers;
- an arbitrary-precision analytic continuation for real and complex decimal arguments.

## Exact integer implementation

The integer API accepts and returns `BigInteger`:

```java
BigInteger result = ParalellFactorial.factorial(n);
```

For practical inputs, it factors the result into prime powers and multiplies them through a weight-balanced parallel product tree using `ForkJoinPool`. Inputs greater than `Integer.MAX_VALUE` use an exact divide-and-conquer fallback whose boundaries remain `BigInteger`.

Run:

```text
programas.ParalellFactorial
```

## Arbitrary-precision real and complex implementation

For non-integer and complex arguments, the program evaluates:

```text
z! = Gamma(z + 1) = exp(LogGamma(z + 1))
```

The API receives a `BigComplex` and an explicit decimal precision:

```java
MathContext context = new MathContext(200, RoundingMode.HALF_EVEN);
BigComplex z = BigComplex.valueOf(
        BigDecimal.ZERO,
        BigDecimalMath.toBigDecimal("1.111111111111111111111111111111111111111")
);

BigComplex result = ComplexFactorial.factorial(z, context);
```

Run:

```text
programas.ComplexFactorial
```

Accepted console formats include:

```text
3.5
2i
-i
3.5+2.25i
3.5-2.25i
1e-20+2e-5i
```

The parser never converts through `double`. Very long decimal components are read with `BigDecimalMath.toBigDecimal(String)`.

### Numerical method

- `BigComplex`, elementary complex functions and arbitrary-precision decimal operations come from `ch.obermuhlner:big-math:2.3.2`.
- `LogGamma` is evaluated using recurrence and Stirling's asymptotic expansion.
- Bernoulli numbers use an incremental Akiyama-Tanigawa cache with an extended internal `MathContext`.
- Negative integers are rejected because `Gamma(z + 1)`, and therefore `z!`, has poles there.

## Requirements

- Java 17 or newer
- Eclipse IDE with Java Development Tools and Maven Integration for Eclipse (`m2e`)
- Maven, when building outside Eclipse

The external dependency is declared in `pom.xml` and downloaded from Maven Central.

## Import into Eclipse

1. Open **File -> Import**.
2. Select **Maven -> Existing Maven Projects**.
3. Select the cloned repository as the root directory.
4. Choose `ParalellFactorial` and finish the import.
5. If necessary, select **Maven -> Update Project** from the project's context menu.
6. Run either main class as a Java application.

## Precision and practical limits

"Arbitrary precision" means that the caller chooses a finite number of significant decimal digits. A transcendental complex result generally has infinitely many non-repeating digits, so no program can return all of them.

Input length and requested precision are not restricted to `double` numeric precision, but Java ultimately remains limited by available memory, execution time, `BigDecimal` scale and array-size limits. The precision itself is a positive `int`, as required by `MathContext`.

The exact integer factorial is limited by the memory needed to store and print the result. The complex continuation is limited by the requested finite precision and the numerical cost of the transcendental operations.
