package programas;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Exact parallel Prime Swing factorial for practical {@code int} arguments.
 *
 * <p>The algorithm computes only the odd part of {@code n!}, recursively
 * squares the odd part of {@code floor(n / 2)!}, multiplies by the swing
 * number and restores the power of two with one final shift. Products are
 * formed as balanced trees so {@link BigInteger} can use its fast large-number
 * multiplication algorithms.</p>
 */
final class PrimeSwingFactorial {

    private static final BigInteger ONE = BigInteger.ONE;
    private static final int PRODUCT_LEAF_SIZE = 32;

    private PrimeSwingFactorial() {
        // Utility class.
    }

    static BigInteger factorial(int n, ForkJoinPool pool) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        if (n < 2) {
            return ONE;
        }

        int[] oddPrimes = oddPrimesUpTo(n);
        BigInteger oddPart = pool.invoke(new OddFactorialTask(n, oddPrimes));
        int exponentOfTwo = n - Integer.bitCount(n);
        return oddPart.shiftLeft(exponentOfTwo);
    }

    private static int[] oddPrimesUpTo(int limit) {
        boolean[] compositeOdd = new boolean[(limit >>> 1) + 1];
        int squareRoot = (int) Math.sqrt(limit);

        for (int prime = 3; prime <= squareRoot; prime += 2) {
            if (!compositeOdd[prime >>> 1]) {
                long step = (long) prime << 1;
                for (long multiple = (long) prime * prime;
                     multiple <= limit;
                     multiple += step) {
                    compositeOdd[(int) (multiple >>> 1)] = true;
                }
            }
        }

        int count = 0;
        for (int value = 3; value > 0 && value <= limit; value += 2) {
            if (!compositeOdd[value >>> 1]) {
                count++;
            }
        }

        int[] primes = new int[count];
        int index = 0;
        for (int value = 3; value > 0 && value <= limit; value += 2) {
            if (!compositeOdd[value >>> 1]) {
                primes[index++] = value;
            }
        }
        return primes;
    }

    private static final class OddFactorialTask extends RecursiveTask<BigInteger> {
        private static final long serialVersionUID = 1L;

        private final int n;
        private final int[] primes;

        private OddFactorialTask(int n, int[] primes) {
            this.n = n;
            this.primes = primes;
        }

        @Override
        protected BigInteger compute() {
            if (n < 2) {
                return ONE;
            }

            OddFactorialTask lowerTask = new OddFactorialTask(n >>> 1, primes);
            lowerTask.fork();
            BigInteger swing = swing(n, primes);
            BigInteger lower = lowerTask.join();
            return lower.multiply(lower).multiply(swing);
        }
    }

    /**
     * Computes the odd swing number
     * product p^(sum(floor(n / p^k) mod 2)) over odd primes p.
     */
    private static BigInteger swing(int n, int[] primes) {
        BigInteger[] factors = new BigInteger[primes.length];
        int count = 0;

        for (int prime : primes) {
            if (prime > n) {
                break;
            }

            int exponent = 0;
            long power = prime;
            while (power <= n) {
                if (((n / power) & 1L) != 0L) {
                    exponent++;
                }
                if (power > n / prime) {
                    break;
                }
                power *= prime;
            }

            if (exponent != 0) {
                factors[count++] = BigInteger.valueOf(prime).pow(exponent);
            }
        }

        if (count == 0) {
            return ONE;
        }
        return new ProductTask(Arrays.copyOf(factors, count), 0, count).invoke();
    }

    private static final class ProductTask extends RecursiveTask<BigInteger> {
        private static final long serialVersionUID = 1L;

        private final BigInteger[] factors;
        private final int from;
        private final int to;

        private ProductTask(BigInteger[] factors, int from, int to) {
            this.factors = factors;
            this.from = from;
            this.to = to;
        }

        @Override
        protected BigInteger compute() {
            int length = to - from;
            if (length <= PRODUCT_LEAF_SIZE) {
                BigInteger product = ONE;
                for (int index = from; index < to; index++) {
                    product = product.multiply(factors[index]);
                }
                return product;
            }

            int middle = from + (length >>> 1);
            ProductTask left = new ProductTask(factors, from, middle);
            ProductTask right = new ProductTask(factors, middle, to);
            left.fork();
            BigInteger rightProduct = right.compute();
            return left.join().multiply(rightProduct);
        }
    }
}
