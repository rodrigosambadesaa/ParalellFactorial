package programas;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Scanner;

import ch.obermuhlner.math.big.BigComplex;
import ch.obermuhlner.math.big.BigComplexMath;

/**
 * Arbitrary-precision analytic continuation of the factorial to real and
 * complex arguments.
 *
 * <pre>
 * z! = Gamma(z + 1) = exp(LogGamma(z + 1))
 * </pre>
 */
public final class ComplexFactorial {

    private static final int GUARD_DIGITS = 18;

    private ComplexFactorial() {
        // Utility class
    }

    /**
     * Interactive entry point.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        System.out.println("=== ARBITRARY-PRECISION COMPLEX FACTORIAL ===");
        System.out.println("Accepted examples: 3.5, 2i, -i, 3.5-2.25i, 1e-20+2e-5i");
        System.out.println("Type q to quit.");

        try (Scanner keyboard = new Scanner(System.in)) {
            while (true) {
                System.out.print(System.lineSeparator() + "Enter z: ");
                if (!keyboard.hasNextLine()) {
                    return;
                }

                String input = keyboard.nextLine().trim();
                if (input.equalsIgnoreCase("q")) {
                    return;
                }

                System.out.print("Significant decimal digits: ");
                if (!keyboard.hasNextLine()) {
                    return;
                }

                String precisionText = keyboard.nextLine().trim();

                try {
                    int precision = Integer.parseInt(precisionText);
                    if (precision <= 0) {
                        throw new IllegalArgumentException("Precision must be a positive integer");
                    }

                    BigComplex z = ComplexInputParser.parse(input);
                    MathContext context = new MathContext(precision, RoundingMode.HALF_EVEN);

                    long start = System.nanoTime();
                    BigComplex result = factorial(z, context);
                    long end = System.nanoTime();

                    System.out.println(input + "! =");
                    System.out.println(format(result));
                    System.out.printf("Calculation time: %.3f s%n", (end - start) / 1_000_000_000.0);
                } catch (NumberFormatException e) {
                    System.out.println("Error: invalid number or precision: " + e.getMessage());
                } catch (IllegalArgumentException | ArithmeticException e) {
                    System.out.println("Error: " + e.getMessage());
                } catch (OutOfMemoryError e) {
                    System.err.println("Error: not enough JVM heap memory for the requested precision.");
                    return;
                }
            }
        }
    }

    /**
     * Calculates the principal analytic continuation {@code z! = Gamma(z+1)}.
     *
     * @param z arbitrary-precision real or complex argument
     * @param resultContext requested significant-digit precision
     * @return {@code z!} rounded to {@code resultContext}
     */
    public static BigComplex factorial(BigComplex z, MathContext resultContext) {
        if (z == null) {
            throw new IllegalArgumentException("The argument cannot be null");
        }
        if (resultContext == null || resultContext.getPrecision() <= 0) {
            throw new IllegalArgumentException("A finite positive output precision is required");
        }

        if (z.re.signum() == 0 && z.im.signum() == 0) {
            return BigComplex.ONE;
        }

        if (z.isReal()
                && z.re.signum() < 0
                && z.re.stripTrailingZeros().scale() <= 0) {
            throw new ArithmeticException("Factorial has a pole at negative integers");
        }

        MathContext workContext = new MathContext(
                Math.addExact(resultContext.getPrecision(), GUARD_DIGITS),
                resultContext.getRoundingMode()
        );

        BigComplex zPlusOne = z.add(BigDecimal.ONE, workContext);
        BigComplex logarithm = ComplexLogGamma.log(zPlusOne, workContext);
        return BigComplexMath.exp(logarithm, workContext).round(resultContext);
    }

    private static String format(BigComplex value) {
        BigDecimal real = normalizeZero(value.re);
        BigDecimal imaginary = normalizeZero(value.im);

        if (imaginary.signum() == 0) {
            return real.stripTrailingZeros().toPlainString();
        }

        String sign = imaginary.signum() < 0 ? " - " : " + ";
        return real.stripTrailingZeros().toPlainString()
                + sign
                + imaginary.abs().stripTrailingZeros().toPlainString()
                + " i";
    }

    private static BigDecimal normalizeZero(BigDecimal value) {
        return value.signum() == 0 ? BigDecimal.ZERO : value;
    }
}
