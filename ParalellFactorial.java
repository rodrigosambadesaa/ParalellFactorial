// Original implementation: https://stackoverflow.com/a/74143513
// Posted by Rodrigo and modified by the Stack Overflow community.
// Retrieved 2026-09-15 under CC BY-SA 4.0.

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Exact arbitrary-precision factorial calculator.
 *
 * <p>The public API accepts and returns {@link BigInteger}. Internally, the
 * implementation chooses among an iterative product, prime decomposition and
 * an arbitrary-size divide-and-conquer fallback.</p>
 */
public final class ParalellFactorial {

    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private static final int SMALL_FACTORIAL_LIMIT = 20_000;
    private static final int PRIME_LEAF_SIZE = 64;
    private static final BigInteger BIG_RANGE_LEAF_SIZE = BigInteger.valueOf(8_192);
    private static final int OUTPUT_WIDTH = 80;
    private static final double LOG2_E = 1.0 / Math.log(2.0);

    private static final ForkJoinPool POOL = ForkJoinPool.commonPool();

    private ParalellFactorial() {
        // Utility class
    }

    /**
     * Runs the interactive factorial calculator.
     *
     * @param args command-line arguments; currently unused
     * @throws IOException if writing the result fails
     */
    public static void main(String[] args) throws IOException {
        System.out.println("=== EXACT PARALLEL FACTORIAL ===");
        System.out.println("Type q to quit.");

        BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8),
                1 << 20
        );

        try (Scanner keyboard = new Scanner(System.in)) {
            while (true) {
                System.out.print(System.lineSeparator() + "Enter a non-negative integer: ");

                if (!keyboard.hasNext()) {
                    System.out.println(System.lineSeparator() + "Program terminated.");
                    return;
                }

                String token = keyboard.next();
                if (token.equalsIgnoreCase("q")) {
                    System.out.println("Program terminated.");
                    return;
                }

                final BigInteger number;
                try {
                    number = new BigInteger(token);
                } catch (NumberFormatException e) {
                    System.out.println("Error: you must enter a valid integer.");
                    continue;
                }

                try {
                    long calculationStart = System.nanoTime();
                    BigInteger result = factorial(number);
                    long calculationEnd = System.nanoTime();

                    long conversionStart = System.nanoTime();
                    String decimalResult = result.toString();
                    long conversionEnd = System.nanoTime();

                    out.write(System.lineSeparator());
                    out.write("Factorial of ");
                    out.write(number.toString());
                    out.write(':');
                    out.write(System.lineSeparator());

                    long outputStart = System.nanoTime();
                    writeWrapped(decimalResult, OUTPUT_WIDTH, out);
                    out.flush();
                    long outputEnd = System.nanoTime();

                    System.out.printf(
                            "%nCalculation time: %.3f s%n",
                            nanosToSeconds(calculationEnd - calculationStart)
                    );
                    System.out.printf(
                            "Decimal conversion time: %.3f s%n",
                            nanosToSeconds(conversionEnd - conversionStart)
                    );
                    System.out.printf(
                            "Console output time: %.3f s%n",
                            nanosToSeconds(outputEnd - outputStart)
                    );
                    System.out.printf(
                            "Total measured time: %.3f s%n",
                            nanosToSeconds(outputEnd - calculationStart)
                    );
                    System.out.println("Number of digits: " + decimalResult.length());
                } catch (IllegalArgumentException e) {
                    System.out.println("Error: " + e.getMessage());
                } catch (ArithmeticException e) {
                    System.err.println("Error: the requested result exceeds a JVM arithmetic limit.");
                } catch (OutOfMemoryError e) {
                    System.err.println("Error: not enough JVM heap memory.");
                    System.err.println("Use a larger -Xmx value or a smaller input.");
                    return;
                }
            }
        }
    }

    /**
     * Calculates {@code n!} exactly.
     *
     * @param n non-negative integer whose factorial is required
     * @return {@code n!}
     * @throws IllegalArgumentException if {@code n} is null or negative
     */
    public static BigInteger factorial(BigInteger n) {
        validateArgument(n);

        if (n.compareTo(ONE) <= 0) {
            return ONE;
        }

        if (n.compareTo(BigInteger.valueOf(SMALL_FACTORIAL_LIMIT)) <= 0) {
            return factorialIterative(n.intValueExact());
        }

        if (n.compareTo(INT_MAX) <= 0) {
            return factorialByPrimeDecomposition(n.intValueExact());
        }

        return POOL.invoke(new BigRangeProductTask(TWO, n));
    }

    private static void validateArgument(BigInteger n) {
        if (n == null) {
            throw new IllegalArgumentException("The argument cannot be null");
        }
        if (n.signum() < 0) {
            throw new IllegalArgumentException("Argument must be a non-negative integer");
        }
    }

    private static BigInteger factorialIterative(int n) {
        BigInteger result = ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    private static BigInteger factorialByPrimeDecomposition(int n) {
        int[] primes = primesUpTo(n);
        PrimeData primeData = preparePrimeData(n, primes);
        return POOL.invoke(new PrimeProductTask(primeData, 0, primes.length));
    }

    /**
     * Returns every prime less than or equal to {@code limit} using an
     * odd-only sieve.
     */
    private static int[] primesUpTo(int limit) {
        if (limit < 2) {
            return new int[0];
        }

        boolean[] compositeOdd = new boolean[(limit >>> 1) + 1];
        int sqrt = (int) Math.sqrt(limit);

        for (int prime = 3; prime <= sqrt; prime += 2) {
            if (!compositeOdd[prime >>> 1]) {
                long step = (long) prime << 1;
                for (long multiple = (long) prime * prime;
                     multiple <= limit;
                     multiple += step) {
                    compositeOdd[(int) (multiple >>> 1)] = true;
                }
            }
        }

        int numberOfPrimes = 1;
        for (int value = 3; value > 0 && value <= limit; value += 2) {
            if (!compositeOdd[value >>> 1]) {
                numberOfPrimes++;
            }
        }

        int[] primes = new int[numberOfPrimes];
        primes[0] = 2;
        int index = 1;

        for (int value = 3; value > 0 && value <= limit; value += 2) {
            if (!compositeOdd[value >>> 1]) {
                primes[index++] = value;
            }
        }

        return primes;
    }

    private static PrimeData preparePrimeData(int n, int[] primes) {
        int[] exponents = new int[primes.length];
        long[] prefixWeights = new long[primes.length + 1];

        for (int i = 0; i < primes.length; i++) {
            int prime = primes[i];
            int exponent = exponentInFactorial(n, prime);
            exponents[i] = exponent;

            long bits = Math.max(
                    1L,
                    (long) Math.ceil(exponent * Math.log(prime) * LOG2_E)
            );
            prefixWeights[i + 1] = prefixWeights[i] + bits;
        }

        return new PrimeData(primes, exponents, prefixWeights);
    }

    /**
     * Calculates a prime's exponent in {@code n!} using Legendre's formula.
     */
    private static int exponentInFactorial(int n, int prime) {
        int exponent = 0;
        int quotient = n;

        while ((quotient /= prime) != 0) {
            exponent += quotient;
        }

        return exponent;
    }

    private static final class PrimeData {
        private final int[] primes;
        private final int[] exponents;
        private final long[] prefixWeights;

        private PrimeData(int[] primes, int[] exponents, long[] prefixWeights) {
            this.primes = primes;
            this.exponents = exponents;
            this.prefixWeights = prefixWeights;
        }
    }

    /**
     * Parallel product tree split by estimated result bit size.
     */
    private static final class PrimeProductTask extends RecursiveTask<BigInteger> {
        private final PrimeData data;
        private final int from;
        private final int to;

        private PrimeProductTask(PrimeData data, int from, int to) {
            this.data = data;
            this.from = from;
            this.to = to;
        }

        @Override
        protected BigInteger compute() {
            int length = to - from;
            if (length <= 0) {
                return ONE;
            }
            if (length <= PRIME_LEAF_SIZE) {
                return multiplyLeaf();
            }

            int middle = weightedMidpoint(data.prefixWeights, from, to);
            if (middle <= from || middle >= to) {
                middle = from + (length >>> 1);
            }

            PrimeProductTask left = new PrimeProductTask(data, from, middle);
            PrimeProductTask right = new PrimeProductTask(data, middle, to);

            left.fork();
            BigInteger rightResult = right.compute();
            BigInteger leftResult = left.join();
            return leftResult.multiply(rightResult);
        }

        private BigInteger multiplyLeaf() {
            int length = to - from;
            BigInteger[] values = new BigInteger[length];

            for (int i = 0; i < length; i++) {
                int index = from + i;
                values[i] = BigInteger.valueOf(data.primes[index])
                        .pow(data.exponents[index]);
            }

            return balancedProduct(values, 0, values.length);
        }
    }

    private static int weightedMidpoint(long[] prefix, int from, int to) {
        long startWeight = prefix[from];
        long rangeWeight = prefix[to] - startWeight;
        long target = startWeight + (rangeWeight >>> 1);

        int low = from + 1;
        int high = to - 1;

        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (prefix[middle] < target) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        return Math.min(Math.max(low, from + 1), to - 1);
    }

    private static BigInteger balancedProduct(BigInteger[] values, int from, int to) {
        int length = to - from;
        if (length <= 0) {
            return ONE;
        }
        if (length == 1) {
            return values[from];
        }

        int middle = from + (length >>> 1);
        BigInteger left = balancedProduct(values, from, middle);
        BigInteger right = balancedProduct(values, middle, to);
        return left.multiply(right);
    }

    /**
     * Exact fallback whose range boundaries remain arbitrary-size integers.
     */
    private static final class BigRangeProductTask extends RecursiveTask<BigInteger> {
        private final BigInteger start;
        private final BigInteger end;

        private BigRangeProductTask(BigInteger start, BigInteger end) {
            this.start = start;
            this.end = end;
        }

        @Override
        protected BigInteger compute() {
            int comparison = start.compareTo(end);
            if (comparison > 0) {
                return ONE;
            }
            if (comparison == 0) {
                return start;
            }

            BigInteger length = end.subtract(start).add(ONE);
            if (length.compareTo(BIG_RANGE_LEAF_SIZE) <= 0) {
                return sequentialRangeProduct(start, end);
            }

            BigInteger middle = start.add(end).shiftRight(1);
            BigRangeProductTask left = new BigRangeProductTask(start, middle);
            BigRangeProductTask right = new BigRangeProductTask(middle.add(ONE), end);

            left.fork();
            BigInteger rightResult = right.compute();
            BigInteger leftResult = left.join();
            return leftResult.multiply(rightResult);
        }
    }

    private static BigInteger sequentialRangeProduct(BigInteger start, BigInteger end) {
        BigInteger result = ONE;
        for (BigInteger value = start;
             value.compareTo(end) <= 0;
             value = value.add(ONE)) {
            result = result.multiply(value);
        }
        return result;
    }

    private static void writeWrapped(String text, int width, BufferedWriter out)
            throws IOException {
        if (width <= 0) {
            throw new IllegalArgumentException("Output width must be positive");
        }

        String lineSeparator = System.lineSeparator();
        for (int start = 0; start < text.length(); start += width) {
            int length = Math.min(width, text.length() - start);
            out.write(text, start, length);
            out.write(lineSeparator);
        }
    }

    private static double nanosToSeconds(long nanoseconds) {
        return nanoseconds / 1_000_000_000.0;
    }
}
