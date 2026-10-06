package programas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ComplexInputParserTest {

    @Test
    void preservesDecimalAndScientificNotation() {
        assertParsed("1,1i", "0", "1.1");
        assertParsed("3.5-2.25i", "3.5", "-2.25");
        assertParsed("1e-20+2e-5i", "1e-20", "+2e-5");
        assertParsed("-i", "0", "-1");
    }

    @Test
    void rejectsMalformedValues() {
        assertThrows(NumberFormatException.class, () -> ComplexInputParser.parse(""));
        assertThrows(NumberFormatException.class, () -> ComplexInputParser.parse("1+2"));
        assertThrows(NumberFormatException.class, () -> ComplexInputParser.parse("1ii"));
        assertThrows(NumberFormatException.class, () -> ComplexInputParser.parse("NaN"));
    }

    private static void assertParsed(String input, String real, String imaginary) {
        ComplexInputParser.ParsedComplex parsed = ComplexInputParser.parse(input);
        assertEquals(real, parsed.real());
        assertEquals(imaginary, parsed.imaginary());
    }
}
