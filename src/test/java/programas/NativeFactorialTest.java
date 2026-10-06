package programas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeFactorialTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void nativeBinaryTransferMatchesKnownFactorial() throws Exception {
        assumeTrue(NativeFactorial.isAvailable());
        NativeFactorial.BinaryResult result = NativeFactorial.factorial(10_000);
        String decimal = result.value().toString();
        assertEquals(35_660, decimal.length());
        assertTrue(decimal.startsWith("2846259680917054518906413212119868890148"));
    }

    @Test
    void nativeDecimalFileHasVerifiedLength() throws Exception {
        assumeTrue(NativeFactorial.isAvailable());
        Path output = temporaryDirectory.resolve("100000-factorial.txt");
        NativeFactorial.DecimalFileResult result =
                NativeFactorial.writeDecimal(100_000, output);
        assertEquals(456_574, result.digits());
        assertEquals(456_574, Files.size(output));
        assertEquals('0', Files.readString(output).charAt(456_573));
    }
}
