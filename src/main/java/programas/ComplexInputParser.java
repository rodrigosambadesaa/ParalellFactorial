package programas;

import java.util.regex.Pattern;

/** Parses decimal complex numbers while preserving every input digit. */
final class ComplexInputParser {

    private static final String DECIMAL_PATTERN =
            "[+-]?(?:(?:\\d+(?:\\.\\d*)?)|(?:\\.\\d+))(?:[eE][+-]?\\d+)?";
    private static final Pattern DECIMAL = Pattern.compile(DECIMAL_PATTERN);

    private ComplexInputParser() {
        // Utility class.
    }

    static ParsedComplex parse(String text) {
        if (text == null) {
            throw new NumberFormatException("The input cannot be null");
        }

        String value = text.trim().replaceAll("\\s+", "").replace(',', '.');
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
            return new ParsedComplex(requireDecimal(value), "0");
        }

        String body = value.substring(0, value.length() - 1);
        int separator = findRealImaginarySeparator(body);
        if (separator < 0) {
            return new ParsedComplex("0", imaginaryCoefficient(body));
        }

        return new ParsedComplex(
                requireDecimal(body.substring(0, separator)),
                imaginaryCoefficient(body.substring(separator))
        );
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

    private static String imaginaryCoefficient(String value) {
        return switch (value) {
            case "", "+" -> "1";
            case "-" -> "-1";
            default -> requireDecimal(value);
        };
    }

    private static String requireDecimal(String value) {
        if (!DECIMAL.matcher(value).matches()) {
            throw new NumberFormatException("Invalid decimal component: " + value);
        }
        return value;
    }

    record ParsedComplex(String real, String imaginary) {
    }
}
