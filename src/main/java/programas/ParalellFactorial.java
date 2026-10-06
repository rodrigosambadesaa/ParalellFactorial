package programas;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/** Exact arbitrary-precision factorial calculator. */
public final class ParalellFactorial {

    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.TWO;
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger BIG_RANGE_LEAF_SIZE = BigInteger.valueOf(8_192);
    private static final int OUTPUT_WIDTH = 80;
    private static final ForkJoinPool POOL = ForkJoinPool.commonPool();

    private ParalellFactorial() {
        // Utility class.
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== EXACT PARALLEL PRIME-SWING FACTORIAL ===");
        System.out.println("Type q to quit.");

        BufferedWriter output = new BufferedWriter(
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8),
                1 << 20
        );

        try (Scanner keyboard = new Scanner(System.in)) {
            while (true) {
                System.out.print(System.lineSeparator() + "Enter a non-negative integer: ");
                if (!keyboard.hasNext()) {
                    return;
                }

                String token = keyboard.next();
                if (token.equalsIgnoreCase("q")) {
                    return;
                }

                try {
                    BigInteger number = new BigInteger(token);
                    long calculationStart = System.nanoTime();
                    BigInteger result = factorial(number);
                    long calculationEnd = System.nanoTime();

                    long conversionStart = System.nanoTime();
                    String decimalResult = result.toString();
                    long conversionEnd = System.nanoTime();

                    output.write(System.lineSeparator());
                    output.write("Factorial of ");
                    output.write(number.toString());
                    output.write(':');
                    output.write(System.lineSeparator());
                    writeWrapped(decimalResult, OUTPUT_WIDTH, output);
                    output.flush();

                    System.out.printf("Calculation time: %.3f s%n",
                            nanosToSeconds(calculationEnd - calculationStart));
                    System.out.printf("Decimal conversion time: %.3f s%n",
                            nanosToSeconds(conversionEnd - conversionStart));
                    System.out.println("Number of digits: " + decimalResult.length());
                } catch (NumberFormatException exception) {
                    System.out.println("Error: you must enter a valid integer.");
                } catch (IllegalArgumentException exception) {
                    System.out.println("Error: " + exception.getMessage());
                } catch (OutOfMemoryError error) {
                    System.err.println("Error: not enough JVM heap memory.");
                    return;
                }
            }
        }
    }

    /**
     * Calculates {@code n!} exactly. Practical {@code int}-sized inputs use
     * parallel Prime Swing; larger bounds retain an exact BigInteger product
     * tree fallback instead of silently narrowing the input.
     */
    public static BigInteger factorial(BigInteger n) {
        validateArgument(n);
        if (n.compareTo(ONE) <= 0) {
            return ONE;
        }
        if (n.compareTo(INT_MAX) <= 0) {
            return PrimeSwingFactorial.factorial(n.intValueExact(), POOL);
        }
        return POOL.invoke(new BigRangeProductTask(TWO, n));
    }

    private static void validateArgument(BigInteger n) {
        if (n == null) {
            throw new IllegalArgumentException("The argument cannot be null");
        }
        if (n.signum() < 0) {
            throw new IllegalArgumentException("Argument must be non-negative");
        }
    }

    private static final class BigRangeProductTask extends RecursiveTask<BigInteger> {
        private static final long serialVersionUID = 1L;

        private final BigInteger start;
        private final BigInteger end;

        private BigRangeProductTask(BigInteger start, BigInteger end) {
            this.start = start;
            this.end = end;
        }

        @Override
        protected BigInteger compute() {
            if (start.compareTo(end) > 0) {
                return ONE;
            }

            BigInteger length = end.subtract(start).add(ONE);
            if (length.compareTo(BIG_RANGE_LEAF_SIZE) <= 0) {
                BigInteger product = ONE;
                for (BigInteger factor = start;
                     factor.compareTo(end) <= 0;
                     factor = factor.add(ONE)) {
                    product = product.multiply(factor);
                }
                return product;
            }

            BigInteger middle = start.add(end).shiftRight(1);
            BigRangeProductTask left = new BigRangeProductTask(start, middle);
            BigRangeProductTask right = new BigRangeProductTask(middle.add(ONE), end);
            left.fork();
            BigInteger rightProduct = right.compute();
            return left.join().multiply(rightProduct);
        }
    }

    private static void writeWrapped(String text, int width, BufferedWriter output)
            throws IOException {
        for (int start = 0; start < text.length(); start += width) {
            int length = Math.min(width, text.length() - start);
            output.write(text, start, length);
            output.write(System.lineSeparator());
        }
    }

    private static double nanosToSeconds(long nanoseconds) {
        return nanoseconds / 1_000_000_000.0;
    }
}
