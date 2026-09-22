package programas;

import java.math.BigDecimal;

import ch.obermuhlner.math.big.BigComplex;
import ch.obermuhlner.math.big.BigDecimalMath;

/**
 * Parses real and complex decimal strings without converting through
 * {@code double}.
 */
final class ComplexInputParser {

    private ComplexInputParser() {
        // Utility class
    }

    /**
     * Supported forms include {@code 3.5}, {@code 2i}, {@code -i},
     * {@code 3.5+2.25i}, {@code 3.5-2.25i} and scientific notation.
     *
     * @param text textual complex number
     * @return parsed arbitrary-precision complex number
     */
    static BigComplex parse(String text) {
        if (text == null) {
            throw new NumberFormatException("The input cannot be null");
        }

        String value = text.trim().replaceAll("\\s+", "");
        if (value.startsWith("(") && value.endsWith(")")) {
            value = value.substring(1, value.length() - 1);
        }
        value = value.replace('I', 'i');

        if (value.isEmpty()) {
            throw new NumberFormatException("The input cannot be empty");
        }

        int firstI = value.indexOf('i');
        if (firstI >= 0 && firstI != value.length() - 1) {
            throw new NumberFormatException("The imaginary unit must be the final character");
        }
        if (firstI != value.lastIndexOf('i')) {
            throw new NumberFormatException("The input contains more than one imaginary unit");
        }

        if (!value.endsWith("i")) {
            return BigComplex.valueOf(parseDecimal(value));
        }

        String body = value.substring(0, value.length() - 1);
        int separator = findRealImaginarySeparator(body);

        if (separator < 0) {
            return BigComplex.valueOf(BigDecimal.ZERO, parseImaginaryCoefficient(body));
        }

        BigDecimal real = parseDecimal(body.substring(0, separator));
        BigDecimal imaginary = parseImaginaryCoefficient(body.substring(separator));
        return BigComplex.valueOf(real, imaginary);
    }

    private static int findRealImaginarySeparator(String value) {
        for (int index = 1; index < value.length(); index++) {
            char current = value.charAt(index);
            char previous = value.charAt(index - 1);

            if ((current == '+' || current == '-') && previous != 'e' && previous != 'E') {
                return index;
            }
        }
        return -1;
    }

    private static BigDecimal parseImaginaryCoefficient(String value) {
        return switch (value) {
            case "", "+" -> BigDecimal.ONE;
            case "-" -> BigDecimal.ONE.negate();
            default -> parseDecimal(value);
        };
    }

    private static BigDecimal parseDecimal(String value) {
        return BigDecimalMath.toBigDecimal(value);
    }
}
