package programas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class ParalellFactorialTest {

    @Test
    void primeSwingMatchesSequentialReferenceThroughTwoThousand() {
        BigInteger expected = BigInteger.ONE;
        for (int n = 0; n <= 2_000; n++) {
            if (n >= 2) {
                expected = expected.multiply(BigInteger.valueOf(n));
            }
            assertEquals(expected, ParalellFactorial.factorial(BigInteger.valueOf(n)),
                    "Mismatch at n=" + n);
        }
    }

    @Test
    void knownLargeFingerprintIsCorrect() {
        String value = ParalellFactorial.factorial(BigInteger.valueOf(10_000)).toString();
        assertEquals(35_660, value.length());
        assertEquals("2846259680917054518906413212119868890148",
                value.substring(0, 40));
    }

    @Test
    void negativeAndNullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ParalellFactorial.factorial(BigInteger.valueOf(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> ParalellFactorial.factorial(null));
    }
}
