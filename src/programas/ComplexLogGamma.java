package programas;

import java.math.BigDecimal;
import java.math.MathContext;

import ch.obermuhlner.math.big.BigComplex;
import ch.obermuhlner.math.big.BigComplexMath;
import ch.obermuhlner.math.big.BigDecimalMath;

/**
 * Arbitrary-precision complex log-gamma evaluated with recurrence and
 * Stirling's asymptotic expansion.
 */
final class ComplexLogGamma {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final int GUARD_DIGITS = 18;
    private static final int MAX_SHIFTS = 1_000_000;
    private static final int MAX_SERIES_TERMS = 1_000_000;

    private ComplexLogGamma() {
        // Utility class
    }

    static BigComplex log(BigComplex argument, MathContext resultContext) {
        if (resultContext == null || resultContext.getPrecision() <= 0) {
            throw new IllegalArgumentException("A finite positive precision is required");
        }
        rejectPole(argument);

        MathContext workContext = new MathContext(
                Math.addExact(resultContext.getPrecision(), GUARD_DIGITS),
                resultContext.getRoundingMode()
        );

        int targetMagnitude = Math.max(
                20,
                Math.addExact(
                        Math.multiplyExact(workContext.getPrecision() + 20, 2) / 5,
                        8
                )
        );
        BigDecimal targetSquared = BigDecimal.valueOf(targetMagnitude)
                .multiply(BigDecimal.valueOf(targetMagnitude), workContext);

        BigComplex shifted = argument;
        int shifts = 0;

        while (shifted.re.signum() <= 0
                || shifted.absSquare(workContext).compareTo(targetSquared) < 0) {
            if (shifts >= MAX_SHIFTS) {
                throw new ArithmeticException(
                        "Log-gamma evaluation requires too many recurrence steps"
                );
            }
            shifted = shifted.add(BigDecimal.ONE, workContext);
            shifts++;
        }

        BigComplex result = stirling(shifted, workContext);

        for (int index = shifts - 1; index >= 0; index--) {
            BigComplex recurrenceFactor = argument.add(BigDecimal.valueOf(index), workContext);
            result = result.subtract(BigComplexMath.log(recurrenceFactor, workContext), workContext);
        }

        return result.round(resultContext);
    }

    private static BigComplex stirling(BigComplex z, MathContext context) {
        BigComplex logZ = BigComplexMath.log(z, context);
        BigDecimal logTwoPi = BigDecimalMath.log(
                BigDecimalMath.pi(context).multiply(TWO, context),
                context
        );

        BigComplex result = z.subtract(HALF, context)
                .multiply(logZ, context)
                .subtract(z, context)
                .add(BigComplex.valueOf(logTwoPi.multiply(HALF, context)), context);

        BigComplex inverseZ = BigComplex.ONE.divide(z, context);
        BigComplex inverseZSquared = inverseZ.multiply(inverseZ, context);
        BigComplex inversePower = inverseZ;

        BigDecimal previousMagnitudeSquared = null;
        BigDecimal toleranceSquared = BigDecimal.ONE.scaleByPowerOfTen(
                Math.multiplyExact(-2, context.getPrecision())
        );

        boolean converged = false;

        for (int k = 1; k <= MAX_SERIES_TERMS; k++) {
            int twiceK = Math.multiplyExact(2, k);
            BigDecimal bernoulli = BernoulliNumbers.value(twiceK, context);
            BigDecimal denominator = BigDecimal.valueOf(twiceK)
                    .multiply(BigDecimal.valueOf(twiceK - 1L), context);
            BigComplex term = inversePower.multiply(
                    bernoulli.divide(denominator, context),
                    context
            );
            BigDecimal magnitudeSquared = term.absSquare(context);

            if (previousMagnitudeSquared != null
                    && magnitudeSquared.compareTo(previousMagnitudeSquared) >= 0) {
                break;
            }

            result = result.add(term, context);

            if (magnitudeSquared.compareTo(toleranceSquared) < 0) {
                converged = true;
                break;
            }

            previousMagnitudeSquared = magnitudeSquared;
            inversePower = inversePower.multiply(inverseZSquared, context);
        }

        if (!converged) {
            throw new ArithmeticException(
                    "The log-gamma asymptotic series did not reach the requested precision"
            );
        }

        return result;
    }

    private static void rejectPole(BigComplex argument) {
        if (argument.isReal()
                && argument.re.signum() <= 0
                && argument.re.stripTrailingZeros().scale() <= 0) {
            throw new ArithmeticException("Gamma has a pole at non-positive integers");
        }
    }
}
