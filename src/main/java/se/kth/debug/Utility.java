package se.kth.debug;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public class Utility {
    private static final Logger logger = Logger.getLogger("Utility");

    private Utility() {}

    /**
     * Concatenates provided classpath with the system set classpath.
     *
     * @param providedClasspath usually the classpath of the compiled project, its tests, and
     *     dependencies
     * @return concatenated string of classpath
     */
    public static String getClasspathForRunningJUnit(String[] providedClasspath) {
        Set<String> classpathCollection = new HashSet<>();

        String[] pathElements =
                System.getProperty("java.class.path").split(System.getProperty("path.separator"));

        classpathCollection.addAll(verifyAndGetClasspath(pathElements));
        classpathCollection.addAll(verifyAndGetClasspath(providedClasspath));

        return String.join(":", classpathCollection);
    }

    private static Set<String> verifyAndGetClasspath(String[] classpath) {
        Set<String> classpathCollection = new HashSet<>();
        for (String cp : classpath) {
            URI uri = new File(cp).toURI();
            try {
                File file = new File(uri.toURL().getFile());
                if (file.exists()) {
                    classpathCollection.add(file.getAbsolutePath());
                }
            } catch (MalformedURLException e) {
                logger.warning(cp + " is not a valid path");
            }
        }
        return classpathCollection;
    }

    /**
     * Returns test separated by space. This is done so that split function can be called correctly.
     *
     * @param tests array of tests
     * @return a string of test classes separated by " "
     */
    public static String parseTests(String[] tests) {

        return String.join(" ", tests);
    }

    /**
     * Returns path to JaCoCo runtime jar. It is used to dynamically set the javaagent of
     * subprocesses.
     *
     * @return org.jacoco.agent-runtime.jar
     * @throws ClassNotFoundException thrown when JaCoCo is not provided as a test dependency
     */
    public static File getJaCoCoJavaagentJar() throws ClassNotFoundException {
        String[] pathElements =
                System.getProperty("java.class.path").split(System.getProperty("path.separator"));
        String jarPattern = "org\\.jacoco\\.agent-\\d\\.\\d\\.\\d-runtime\\.jar";
        Optional<File> jaCoCoCandidate =
                Arrays.stream(pathElements)
                        .map(File::new)
                        .filter(f -> f.getName().matches(jarPattern))
                        .findFirst();

        if (jaCoCoCandidate.isPresent()) {
            return jaCoCoCandidate.get();
        }
        throw new ClassNotFoundException("JaCoCo is not provided as a test dependency");
    }
}
