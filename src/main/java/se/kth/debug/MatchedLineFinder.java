package se.kth.debug;

import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.support.SpoonSupport;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.CtScanner;

public class MatchedLineFinder {
    private static final Logger LOGGER = Logger.getLogger("MatchedLineFinder");

    public static void main(String[] args) throws Exception {
        File project = new File(args[0]);
        File diffedFile = getAbsolutePathWithGivenBase(project, args[1]);
        String left = args[2];
        String right = args[3];

        final String LEFT_FOLDER_NAME = "gumtree-left";
        final String RIGHT_FOLDER_NAME = "gumtree-right";

        File leftJava = prepareFileForGumtree(project, left, diffedFile, LEFT_FOLDER_NAME);
        File rightJava = prepareFileForGumtree(project, right, diffedFile, RIGHT_FOLDER_NAME);

        Diff diff = new AstComparator().compare(leftJava, rightJava);
        Set<Integer> diffLines = getDiffLines(diff.getRootOperations());

        CtMethod<?> methodLeft = findMethod(diff.getRootOperations());
        CtMethod<?> methodRight =
                (CtMethod<?>) new SpoonSupport().getMappedElement(diff, methodLeft, true);
        Set<Integer> matchedLinesLeft = getMatchedLines(diffLines, methodLeft);
        Set<Integer> matchedLinesRight = getMatchedLines(diffLines, methodRight);
        String fullyQualifiedNameOfContainerClass =
                methodLeft.getParent(CtClass.class).getQualifiedName();

        String outputLeft =
                String.format(
                        "%s=%s%n",
                        fullyQualifiedNameOfContainerClass,
                        StringUtils.join(matchedLinesLeft, ","));
        String outputRight =
                String.format(
                        "%s=%s%n",
                        fullyQualifiedNameOfContainerClass,
                        StringUtils.join(matchedLinesRight, ","));
        writeToFile(outputLeft, "input-left.txt");
        writeToFile(outputRight, "input-right.txt");
    }

    private static Set<Integer> getDiffLines(List<Operation> rootOperations) {
        Set<Integer> result = new HashSet<>();
        rootOperations.forEach(
                operation -> {
                    if (operation.getSrcNode() != null) {
                        result.add(operation.getSrcNode().getPosition().getLine());
                    }
                    if (operation.getDstNode() != null) {
                        result.add(operation.getSrcNode().getPosition().getLine());
                    }
                });
        return Collections.unmodifiableSet(result);
    }

    static class BlockFinder extends CtScanner {
        private final Set<Integer> diffLines;
        private final Set<Integer> lines = new HashSet<>();

        private BlockFinder(Set<Integer> diffLines) {
            this.diffLines = diffLines;
        }

        /**
         * Get line numbers of statements, excluding diff lines, within a block.
         *
         * @param block element to be traversed
         * @param <R> return type of block, if any
         */
        @Override
        public <R> void visitCtBlock(CtBlock<R> block) {
            List<CtStatement> statements = block.getStatements();
            statements.forEach(
                    statement -> {
                        if (!diffLines.contains(statement.getPosition().getLine())) {
                            lines.add(statement.getPosition().getLine());
                        }
                    });
            super.visitCtBlock(block);
        }

        public Set<Integer> getLines() {
            return lines;
        }
    }

    private static Set<Integer> getMatchedLines(Set<Integer> diffLines, CtMethod<?> method) {
        BlockFinder blockTraversal = new BlockFinder(diffLines);
        blockTraversal.scan(method);
        return blockTraversal.getLines();
    }

    private static CtMethod<?> findMethod(List<Operation> rootOperations) {
        // In an ideal case, srcNode of first root operation will give the method because APR
        // patches usually have
        // only one operation.
        // We also return the first method we find because we assume there will a patch inside only
        // one method.
        for (Operation<?> operation : rootOperations) {
            CtMethod<?> candidate = operation.getSrcNode().getParent(CtMethod.class);
            if (candidate == null) {
                LOGGER.warning(
                        operation.getSrcNode()
                                + ":"
                                + operation.getSrcNode().getPosition().getLine()
                                + " has no parent method.");
            } else {
                return candidate;
            }
        }
        throw new RuntimeException("No diff line is enclosed in method");
    }

    private static File getAbsolutePathWithGivenBase(File base, String filename) {
        List<File> absolutePath =
                FileUtils.listFiles(new File(base, "src"), new String[] {"java"}, true).stream()
                        .filter(file -> file.getName().equals(filename))
                        .collect(Collectors.toList());

        if (absolutePath.isEmpty()) {
            throw new RuntimeException(filename + " does not exist in " + base.getAbsolutePath());
        }
        if (absolutePath.size() > 1) {
            throw new RuntimeException("Use fully qualified names");
        }
        return absolutePath.get(0);
    }

    private static File prepareFileForGumtree(
            File cwd, String commit, File diffedFile, String revision)
            throws IOException, InterruptedException {
        if (checkout(cwd, commit) == 0) {
            return copy(cwd, diffedFile, revision);
        }
        throw new RuntimeException("Error occurred in checking out.");
    }

    private static int checkout(File cwd, String commit) throws IOException, InterruptedException {
        ProcessBuilder checkoutBuilder = new ProcessBuilder("git", "checkout", commit);
        checkoutBuilder.directory(cwd);
        Process p = checkoutBuilder.start();
        return p.waitFor();
    }

    private static File copy(File cwd, File diffedFile, String revision)
            throws IOException, InterruptedException {
        final File revisionDirectory = new File(cwd.toURI().resolve(revision));
        revisionDirectory.mkdir();

        ProcessBuilder cpBuilder =
                new ProcessBuilder(
                        "cp",
                        diffedFile.getAbsolutePath(),
                        revisionDirectory.toURI().resolve(diffedFile.getName()).getPath());
        cpBuilder.directory(cwd);
        Process p = cpBuilder.start();
        p.waitFor();

        return new File(revisionDirectory.toURI().resolve(diffedFile.getName()).getPath());
    }

    private static void writeToFile(String content, String filename) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(content);
        }
    }
}
