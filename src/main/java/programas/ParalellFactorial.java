package programas;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/** Exact arbitrary-precision factorial calculator. */
public final class ParalellFactorial {

    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.TWO;
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger BIG_RANGE_LEAF_SIZE = BigInteger.valueOf(8_192);
    private static final int NATIVE_THRESHOLD = 100_000;
    private static final int DECIMAL_FILE_THRESHOLD = 1_000_000;
    private static final int OUTPUT_WIDTH = 80;
    private static final ForkJoinPool POOL = ForkJoinPool.commonPool();

    private ParalellFactorial() {
        // Utility class.
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length >= 2) {
            BigInteger number = new BigInteger(args[0]);
            DecimalFileResult result = writeDecimalFactorial(number, Path.of(args[1]));
            printFileResult(args[1], result);
            return;
        }

        System.out.println("=== EXACT HIGH-PERFORMANCE FACTORIAL ===");
        System.out.println("FLINT/GMP native backend with parallel Prime Swing fallback");
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
                    validateArgument(number);

                    if (number.compareTo(BigInteger.valueOf(DECIMAL_FILE_THRESHOLD)) >= 0
                            && number.compareTo(INT_MAX) <= 0
                            && NativeFactorial.isAvailable()) {
                        Path destination = Path.of("factorial-" + number + ".txt");
                        DecimalFileResult fileResult = writeDecimalFactorial(number, destination);
                        printFileResult(destination.toString(), fileResult);
                        continue;
                    }

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
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    System.err.println("Factorial calculation interrupted.");
                    return;
                } catch (OutOfMemoryError error) {
                    System.err.println("Error: not enough JVM heap memory.");
                    return;
                }
            }
        }
    }

    /**
     * Calculates {@code n!} exactly. Large practical inputs use FLINT/GMP and
     * are imported directly from a binary magnitude. Prime Swing remains the
     * pure-Java fallback.
     */
    public static BigInteger factorial(BigInteger n) {
        validateArgument(n);
        if (n.compareTo(ONE) <= 0) {
            return ONE;
        }
        if (n.compareTo(INT_MAX) <= 0) {
            int value = n.intValueExact();
            if (value >= NATIVE_THRESHOLD && NativeFactorial.isAvailable()) {
                try {
                    return NativeFactorial.factorial(value).value();
                } catch (IOException exception) {
                    // Preserve exact functionality when the optional native
                    // backend is unavailable or fails to start.
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Native factorial was interrupted", exception);
                }
            }
            return PrimeSwingFactorial.factorial(value, POOL);
        }
        return POOL.invoke(new BigRangeProductTask(TWO, n));
    }

    /**
     * Calculates and writes {@code n!} directly as decimal text. This avoids
     * Java's costly {@link BigInteger#toString()} for giant factorials.
     */
    public static DecimalFileResult writeDecimalFactorial(BigInteger n, Path output)
            throws IOException, InterruptedException {
        validateArgument(n);
        if (n.compareTo(INT_MAX) <= 0 && NativeFactorial.isAvailable()) {
            NativeFactorial.DecimalFileResult result =
                    NativeFactorial.writeDecimal(n.intValueExact(), output);
            return new DecimalFileResult(
                    "FLINT/GMP",
                    result.digits(),
                    result.bitLength(),
                    result.calculationSeconds(),
                    result.conversionSeconds(),
                    result.writeSeconds(),
                    result.sha256()
            );
        }

        long calculationStart = System.nanoTime();
        BigInteger value = factorial(n);
        long calculationEnd = System.nanoTime();
        long conversionStart = System.nanoTime();
        String decimal = value.toString();
        long conversionEnd = System.nanoTime();
        long writeStart = System.nanoTime();
        Files.writeString(output, decimal, StandardCharsets.US_ASCII);
        long writeEnd = System.nanoTime();
        return new DecimalFileResult(
                "Java BigInteger",
                decimal.length(),
                value.bitLength(),
                nanosToSeconds(calculationEnd - calculationStart),
                nanosToSeconds(conversionEnd - conversionStart),
                nanosToSeconds(writeEnd - writeStart),
                "not-calculated"
        );
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

    private static void printFileResult(String output, DecimalFileResult result) {
        System.out.println("Result written to " + Path.of(output).toAbsolutePath());
        System.out.println("Backend: " + result.backend());
        System.out.printf("Calculation time: %.3f s%n", result.calculationSeconds());
        System.out.printf("Decimal conversion time: %.3f s%n", result.conversionSeconds());
        System.out.printf("File write time: %.3f s%n", result.writeSeconds());
        System.out.println("Number of digits: " + result.digits());
        System.out.println("Bit length: " + result.bitLength());
        System.out.println("SHA-256: " + result.sha256());
    }

    /** Timings and integrity data for direct decimal-file output. */
    public record DecimalFileResult(
            String backend,
            long digits,
            long bitLength,
            double calculationSeconds,
            double conversionSeconds,
            double writeSeconds,
            String sha256
    ) {
    }
}
