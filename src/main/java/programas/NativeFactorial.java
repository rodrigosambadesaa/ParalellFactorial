package programas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Fast native integer factorial backed by FLINT/GMP. */
final class NativeFactorial {

    private static final String WORKER_RESOURCE = "/native_factorial.py";
    private static final String PYTHON_ENVIRONMENT_VARIABLE = "PYTHON";

    private NativeFactorial() {
        // Utility class.
    }

    static boolean isAvailable() {
        try {
            String python = findPython();
            Process process = new ProcessBuilder(
                    python, "-c", "from flint import fmpz"
            ).redirectErrorStream(true).start();
            try (InputStream output = process.getInputStream()) {
                output.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static BinaryResult factorial(int n) throws IOException, InterruptedException {
        Path binary = Files.createTempFile("factorial-", ".bin");
        try {
            WorkerResult worker = run("binary", n, binary);
            byte[] magnitude = Files.readAllBytes(binary);
            verifyHash(magnitude, worker.sha256());
            BigInteger value = magnitude.length == 0
                    ? BigInteger.ZERO
                    : new BigInteger(1, magnitude);
            if (value.bitLength() != worker.bitLength()) {
                throw new IOException("Native factorial bit-length verification failed");
            }
            return new BinaryResult(value, worker.calculationSeconds(),
                    worker.conversionSeconds(), worker.writeSeconds());
        } finally {
            Files.deleteIfExists(binary);
        }
    }

    static DecimalFileResult writeDecimal(int n, Path output)
            throws IOException, InterruptedException {
        Path absolute = output.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryDirectory = parent != null
                ? parent
                : Path.of(".").toAbsolutePath();
        Path temporary = Files.createTempFile(
                temporaryDirectory, ".factorial-", ".tmp");
        try {
            WorkerResult worker = run("decimal", n, temporary);
            if (Files.size(temporary) != worker.digits()) {
                throw new IOException("Native factorial decimal-length verification failed");
            }
            verifyFileHash(temporary, worker.sha256());
            moveIntoPlace(temporary, absolute);
            return new DecimalFileResult(
                    worker.digits(),
                    worker.bitLength(),
                    worker.calculationSeconds(),
                    worker.conversionSeconds(),
                    worker.writeSeconds(),
                    worker.sha256()
            );
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static WorkerResult run(String mode, int n, Path output)
            throws IOException, InterruptedException {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }

        Path worker = extractWorker();
        try {
            Process process = new ProcessBuilder(
                    findPython(), worker.toString(), mode,
                    Integer.toString(n), output.toString()
            ).redirectErrorStream(true).start();

            Map<String, String> fields = new HashMap<>();
            StringBuilder diagnostics = new StringBuilder();
            try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int separator = line.indexOf('\t');
                    if (separator > 0) {
                        fields.put(line.substring(0, separator), line.substring(separator + 1));
                    } else {
                        diagnostics.append(line).append('\n');
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("FLINT/GMP factorial worker failed with exit code "
                        + exitCode + ":\n" + diagnostics);
            }

            return new WorkerResult(
                    Long.parseLong(requireField(fields, "DIGITS")),
                    Long.parseLong(requireField(fields, "BIT_LENGTH")),
                    Double.parseDouble(requireField(fields, "CALCULATION_SECONDS")),
                    Double.parseDouble(requireField(fields, "CONVERSION_SECONDS")),
                    Double.parseDouble(requireField(fields, "WRITE_SECONDS")),
                    requireField(fields, "SHA256")
            );
        } finally {
            Files.deleteIfExists(worker);
        }
    }

    private static Path extractWorker() throws IOException {
        try (InputStream input = NativeFactorial.class.getResourceAsStream(WORKER_RESOURCE)) {
            if (input == null) {
                throw new IOException("Bundled native factorial worker not found");
            }
            Path worker = Files.createTempFile("native-factorial-", ".py");
            Files.copy(input, worker, StandardCopyOption.REPLACE_EXISTING);
            return worker;
        }
    }

    private static String findPython() throws IOException, InterruptedException {
        String configured = System.getenv(PYTHON_ENVIRONMENT_VARIABLE);
        if (configured != null && !configured.isBlank() && canRun(configured)) {
            return configured;
        }
        for (String candidate : new String[] {"python3", "python"}) {
            if (canRun(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Python 3 was not found");
    }

    private static boolean canRun(String command) throws InterruptedException {
        try {
            Process process = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true).start();
            try (InputStream output = process.getInputStream()) {
                output.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        }
    }

    private static String requireField(Map<String, String> fields, String name)
            throws IOException {
        String value = fields.get(name);
        if (value == null) {
            throw new IOException("Native worker omitted field " + name);
        }
        return value;
    }

    private static void verifyHash(byte[] payload, String expected) throws IOException {
        String actual = HexFormat.of().formatHex(digest().digest(payload));
        if (!actual.equals(expected)) {
            throw new IOException("Native factorial SHA-256 verification failed");
        }
    }

    private static void verifyFileHash(Path file, String expected) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 20];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(expected)) {
            throw new IOException("Native factorial output SHA-256 verification failed");
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record BinaryResult(
            BigInteger value,
            double calculationSeconds,
            double conversionSeconds,
            double writeSeconds
    ) {
    }

    record DecimalFileResult(
            long digits,
            long bitLength,
            double calculationSeconds,
            double conversionSeconds,
            double writeSeconds,
            String sha256
    ) {
    }

    private record WorkerResult(
            long digits,
            long bitLength,
            double calculationSeconds,
            double conversionSeconds,
            double writeSeconds,
            String sha256
    ) {
    }
}
