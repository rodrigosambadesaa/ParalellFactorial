package programas;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Incremental Bernoulli-number cache using the Akiyama-Tanigawa algorithm.
 *
 * <p>Calculations use substantially more precision than the requested result
 * to absorb the cancellation inherent in the triangular recurrence.</p>
 */
final class BernoulliNumbers {

    private static final Map<CacheKey, DecimalTable> TABLES = new HashMap<>();

    private BernoulliNumbers() {
        // Utility class
    }

    static BigDecimal value(int index, MathContext context) {
        if (index < 0) {
            throw new IllegalArgumentException("Bernoulli index cannot be negative");
        }

        CacheKey key = new CacheKey(context.getPrecision(), context.getRoundingMode());
        DecimalTable table;

        synchronized (TABLES) {
            table = TABLES.computeIfAbsent(key, ignored -> new DecimalTable(context));
        }

        return table.value(index, context);
    }

    private record CacheKey(int precision, RoundingMode roundingMode) {
    }

    private static final class DecimalTable {

        private final MathContext workContext;
        private final List<BigDecimal> work = new ArrayList<>();
        private final List<BigDecimal> values = new ArrayList<>();

        private DecimalTable(MathContext resultContext) {
            int guardDigits = Math.max(40, Math.multiplyExact(2, resultContext.getPrecision()));
            this.workContext = new MathContext(
                    Math.addExact(resultContext.getPrecision(), guardDigits),
                    resultContext.getRoundingMode()
            );

            work.add(BigDecimal.ONE);
            values.add(BigDecimal.ONE);
        }

        private synchronized BigDecimal value(int index, MathContext resultContext) {
            ensureCalculated(index);
            BigDecimal result = values.get(index);

            // Akiyama-Tanigawa produces the second convention B1 = +1/2.
            if (index == 1) {
                result = result.negate();
            }

            return result.round(resultContext);
        }

        private void ensureCalculated(int maximumIndex) {
            for (int m = values.size(); m <= maximumIndex; m++) {
                work.add(BigDecimal.ONE.divide(BigDecimal.valueOf(m + 1L), workContext));

                for (int j = m; j >= 1; j--) {
                    BigDecimal updated = work.get(j - 1)
                            .subtract(work.get(j), workContext)
                            .multiply(BigDecimal.valueOf(j), workContext);
                    work.set(j - 1, updated);
                }

                values.add(work.get(0));
            }
        }
    }
}
