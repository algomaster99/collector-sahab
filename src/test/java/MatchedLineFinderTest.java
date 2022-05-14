import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import se.kth.debug.MatchedLineFinder;
import se.kth.debug.struct.FileAndBreakpoint;

class MatchedLineFinderTest {
    static final Path BASE_DIR = Paths.get("src/test/resources/matched-line-finder");

    @ParameterizedTest
    @ArgumentsSource(ResourceProvider.Patch.class)
    void should_correctlyGenerateAllInputFilesForCollectorSahab(
            ResourceProvider.TestResource sources) throws Exception {
        // ToDo: Can be fixed after https://github.com/SpoonLabs/gumtree-spoon-ast-diff/issues/245
        if (sources.dir.equals("nested-lambda")) {
            assumeTrue(false);
        }
        Pair<String, String> inputsForCollectorSahab =
                MatchedLineFinder.invoke(sources.left, sources.right);
        assertInputsAreAsExpected(inputsForCollectorSahab, sources.expected);
    }

    private void assertInputsAreAsExpected(
            Pair<String, String> input, Path dirContainingExpectedFiles) throws IOException {
        List<FileAndBreakpoint> actualBreakpointLeft =
                deserialiseFileAndBreakpoint(input.getLeft());
        List<FileAndBreakpoint> actualBreakpointRight =
                deserialiseFileAndBreakpoint(input.getRight());

        List<FileAndBreakpoint> expectedBreakpointLeft =
                deserialiseFileAndBreakpoint(
                        Files.readString(dirContainingExpectedFiles.resolve("input-left.txt")));
        List<FileAndBreakpoint> expectedBreakpointRight =
                deserialiseFileAndBreakpoint(
                        Files.readString(dirContainingExpectedFiles.resolve("input-right.txt")));

        assertThat(
                actualBreakpointLeft,
                containsInAnyOrder(expectedBreakpointLeft.toArray(new FileAndBreakpoint[0])));
        assertThat(
                actualBreakpointRight,
                containsInAnyOrder(expectedBreakpointRight.toArray(new FileAndBreakpoint[0])));
    }

    private List<FileAndBreakpoint> deserialiseFileAndBreakpoint(String json) {
        final Gson gson = new Gson();
        return gson.fromJson(json, new TypeToken<List<FileAndBreakpoint>>() {}.getType());
    }

    @Test
    void throwsNoDiffException_whenThereIsNoDiffLinePresent() {
        // arrange
        File left = BASE_DIR.resolve("EXCLUDE_no-diff").resolve("left.java").toFile();
        File right = BASE_DIR.resolve("EXCLUDE_no-diff").resolve("right.java").toFile();

        // assert
        assertThrowsExactly(
                MatchedLineFinder.NoDiffException.class,
                () -> MatchedLineFinder.invoke(left, right));
    }
}

class ResourceProvider {
    static class TestResource {
        String dir;
        File left;
        File right;
        Path expected;

        private TestResource(String dir, File left, File right, Path expected) {
            this.dir = dir;
            this.left = left;
            this.right = right;
            this.expected = expected;
        }

        private static TestResource fromTestDirectory(File testDir) {
            String dir = testDir.getName();
            File left = testDir.toPath().resolve("left.java").toFile();
            File right = testDir.toPath().resolve("right.java").toFile();
            Path expected = testDir.toPath().resolve("expected");
            return new TestResource(dir, left, right, expected);
        }

        @Override
        public String toString() {
            return dir;
        }
    }

    static class Patch implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return Arrays.stream(
                            Objects.requireNonNull(
                                    MatchedLineFinderTest.BASE_DIR.toFile().listFiles()))
                    .filter(File::isDirectory)
                    .filter(dir -> !dir.getName().startsWith("EXCLUDE_"))
                    .map(TestResource::fromTestDirectory)
                    .map(Arguments::of);
        }
    }
}
