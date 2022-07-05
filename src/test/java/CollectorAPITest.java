import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;

import com.sun.jdi.AbsentInformationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import se.kth.debug.Collector;
import se.kth.debug.CollectorOptions;
import se.kth.debug.EventProcessor;
import se.kth.debug.struct.result.*;

public class CollectorAPITest {

    private static CollectorOptions setExecutionDepth(int executionDepth) {
        CollectorOptions context = TestHelper.getDefaultOptions();
        context.setExecutionDepth(executionDepth);
        return context;
    }

    @Test
    void invoke_nonStaticFieldsOfStaticClassesShouldNotBeCollected()
            throws AbsentInformationException, FileNotFoundException {
        // arrange
        String[] classpath =
                TestHelper.getMavenClasspathFromBuildDirectory(
                        TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
        String[] tests = new String[] {"foo.StaticClassFieldTest::test_doSomething"};
        File classesAndBreakpoints =
                TestHelper.PATH_TO_INPUT.resolve("static-class-field.txt").toFile();

        // act
        EventProcessor eventProcessor =
                Collector.invoke(
                        classpath,
                        tests,
                        classesAndBreakpoints,
                        TestHelper.getDefaultOptions().setSkipReturnValues(true));
        BreakPointContext bp =
                eventProcessor.getBreakpointContexts().stream()
                        .filter(bpc -> bpc.getLineNumber() == 24)
                        .findAny()
                        .orElseThrow();
        StackFrameContext sf = bp.getStackFrameContexts().get(0);

        // assert
        assertThat(sf.getRuntimeValueCollection(), is(empty()));
        assertThat(eventProcessor.getReturnValues(), is(empty()));
    }

    @Nested
    class RepresentingCollections {
        @Test
        void invoke_recordValuesFromArrayFieldInsideCollection()
                throws AbsentInformationException, FileNotFoundException {
            // arrange
            String[] classpath =
                    TestHelper.getMavenClasspathFromBuildDirectory(
                            TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
            String[] tests = new String[] {"foo.CollectionsTest::test_returnTruthy"};
            File classesAndBreakpoints =
                    TestHelper.PATH_TO_INPUT
                            .resolve("collections")
                            .resolve("one-level.txt")
                            .toFile();

            // act
            EventProcessor eventProcessor =
                    Collector.invoke(
                            classpath,
                            tests,
                            classesAndBreakpoints,
                            setExecutionDepth(1).setSkipReturnValues(true));

            BreakPointContext breakpoint = eventProcessor.getBreakpointContexts().get(0);
            StackFrameContext stackFrameContext = breakpoint.getStackFrameContexts().get(0);
            List<RuntimeValue> runtimeValues = stackFrameContext.getRuntimeValueCollection();

            // assert
            assertThat(eventProcessor.getReturnValues(), is(empty()));

            RuntimeValue queue = runtimeValues.get(0);
            FieldData arrayContainingQueueElements = queue.getFields().get(0);

            assertThat(queue.getKind(), is(RuntimeValueKind.LOCAL_VARIABLE));
            assertThat(arrayContainingQueueElements.getName(), equalTo("elements"));
            assertThat(
                    arrayContainingQueueElements.getValue(), equalTo(List.of("Added at runtime")));

            RuntimeValue list = runtimeValues.get(1);
            FieldData arrayContainingListElements = list.getFields().get(1);

            assertThat(list.getKind(), is(RuntimeValueKind.FIELD));
            assertThat(arrayContainingListElements.getName(), equalTo("elements"));
            assertThat(arrayContainingListElements.getValue(), equalTo(List.of(1, 2, 3, 4, 5)));

            RuntimeValue set = runtimeValues.get(2);
            FieldData arrayContainingSetElements = set.getFields().get(1);

            assertThat(set.getKind(), is(RuntimeValueKind.FIELD));
            assertThat(arrayContainingSetElements.getName(), equalTo("elements"));
            assertThat(
                    (List<String>) arrayContainingSetElements.getValue(),
                    containsInAnyOrder("aman", "sahab", "sharma"));
        }

        @Test
        void invoke_primitiveArraysAreRecorded()
                throws AbsentInformationException, FileNotFoundException {
            // arrange
            String[] classpath =
                    TestHelper.getMavenClasspathFromBuildDirectory(
                            TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
            String[] tests = new String[] {"foo.CollectionsTest::test_canWePrintPrimitive"};
            File classesAndBreakpoints =
                    TestHelper.PATH_TO_INPUT
                            .resolve("collections")
                            .resolve("primitive.txt")
                            .toFile();

            // act
            EventProcessor eventProcessor =
                    Collector.invoke(
                            classpath,
                            tests,
                            classesAndBreakpoints,
                            TestHelper.getDefaultOptions());

            BreakPointContext breakpoint = eventProcessor.getBreakpointContexts().get(0);
            StackFrameContext stackFrameContext = breakpoint.getStackFrameContexts().get(0);
            RuntimeValue thePrimitiveArray = stackFrameContext.getRuntimeValueCollection().get(0);
            List<String> actualElements = (List<String>) thePrimitiveArray.getValue();

            // assert
            assertThat(eventProcessor.getReturnValues(), is(empty()));

            assertThat(actualElements, equalTo(List.of("yes", "we", "can")));
        }

        @Nested
        class NestedArraysAreRepresentedCorrectly {
            private StackFrameContext arrangeAndAct(int executionDepth)
                    throws AbsentInformationException, FileNotFoundException {
                // arrange
                String[] classpath =
                        TestHelper.getMavenClasspathFromBuildDirectory(
                                TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
                String[] tests =
                        new String[] {"foo.CollectionsTest::test_canNestedArrayBeRepresented"};
                File classesAndBreakpoints =
                        TestHelper.PATH_TO_INPUT
                                .resolve("collections")
                                .resolve("nested-array.txt")
                                .toFile();

                // act
                EventProcessor eventProcessor =
                        Collector.invoke(
                                classpath,
                                tests,
                                classesAndBreakpoints,
                                setExecutionDepth(executionDepth).setSkipReturnValues(true));
                assertThat(eventProcessor.getReturnValues(), is(empty()));
                return eventProcessor.getBreakpointContexts().get(0).getStackFrameContexts().get(0);
            }

            @Test
            void invoke_arrayDepth0() throws AbsentInformationException, FileNotFoundException {
                StackFrameContext sfc = arrangeAndAct(0);
                List<RuntimeValue> runtimeValueCollection = sfc.getRuntimeValueCollection();

                RuntimeValue primitiveIntArray = runtimeValueCollection.get(0);
                assertThat(primitiveIntArray.getKind(), is(RuntimeValueKind.FIELD));
                assertThat(primitiveIntArray.getValue(), equalTo(List.of("int[][]", "int[][]")));
            }

            @Test
            void invoke_arrayDepth1() throws AbsentInformationException, FileNotFoundException {
                StackFrameContext sfc = arrangeAndAct(1);
                List<RuntimeValue> runtimeValueCollection = sfc.getRuntimeValueCollection();

                RuntimeValue primitiveIntArray = runtimeValueCollection.get(0);
                List<ArrayElement> nestedObjects = primitiveIntArray.getArrayElements();

                assertThat(nestedObjects.size(), equalTo(2));
                assertThat(nestedObjects.get(0).getValue(), equalTo(List.of("int[]", "int[]")));
                assertThat(nestedObjects.get(1).getValue(), equalTo(List.of("int[]", "int[]")));
            }

            @Test
            void invoke_arrayDepth2() throws AbsentInformationException, FileNotFoundException {
                StackFrameContext sfc = arrangeAndAct(2);
                List<RuntimeValue> runtimeValueCollection = sfc.getRuntimeValueCollection();

                RuntimeValue primitiveIntArray = runtimeValueCollection.get(0);
                List<ArrayElement> nestedObject1 =
                        primitiveIntArray.getArrayElements().get(0).getArrayElements();
                List<ArrayElement> nestedObject2 =
                        primitiveIntArray.getArrayElements().get(1).getArrayElements();

                assertThat(nestedObject1.get(0).getValue(), equalTo(List.of(1)));
                assertThat(nestedObject1.get(1).getValue(), equalTo(List.of(2)));
                assertThat(nestedObject2.get(0).getValue(), equalTo(List.of(3, 4, 5)));
                assertThat(nestedObject2.get(1).getValue(), equalTo(List.of(5, 3)));
            }
        }

        @Test
        void invoke_elementsInsideNestedSetAreRecorded()
                throws AbsentInformationException, FileNotFoundException {
            // arrange
            String[] classpath =
                    TestHelper.getMavenClasspathFromBuildDirectory(
                            TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
            String[] tests =
                    new String[] {"foo.CollectionsTest::test_canWeRepresentNestedCollection"};
            File classesAndBreakpoints =
                    TestHelper.PATH_TO_INPUT
                            .resolve("collections")
                            .resolve("nested-collection.txt")
                            .toFile();

            // act
            EventProcessor eventProcessor =
                    Collector.invoke(
                            classpath,
                            tests,
                            classesAndBreakpoints,
                            setExecutionDepth(8).setSkipReturnValues(true));

            // assert
            assertThat(eventProcessor.getReturnValues(), is(empty()));

            RuntimeValue onlyNestedSet =
                    eventProcessor
                            .getBreakpointContexts()
                            .get(0)
                            .getStackFrameContexts()
                            .get(0)
                            .getRuntimeValueCollection()
                            .get(0);
            ArrayElement innerMostSet =
                    onlyNestedSet
                            .getFields()
                            .get(1)
                            .getFields()
                            .get(7)
                            .getArrayElements()
                            .get(0)
                            .getFields()
                            .get(1)
                            .getFields()
                            .get(1)
                            .getFields()
                            .get(7)
                            .getArrayElements()
                            .get(0);

            assertThat(
                    innerMostSet.getFields().get(1).getValue(),
                    equalTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        }
    }

    @Nested
    class RepresentingObjects {
        @Test
        void fieldInsideAOneLevelObjectShouldBeRecorded()
                throws AbsentInformationException, FileNotFoundException {
            // arrange
            String[] classpath =
                    TestHelper.getMavenClasspathFromBuildDirectory(
                            TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
            String[] tests = new String[] {"foo.ObjectsTest::justOneLevel"};
            File classesAndBreakpoints =
                    TestHelper.PATH_TO_INPUT
                            .resolve("objects")
                            .resolve("one-level-nesting.txt")
                            .toFile();

            // act
            EventProcessor eventProcessor =
                    Collector.invoke(
                            classpath,
                            tests,
                            classesAndBreakpoints,
                            setExecutionDepth(1).setSkipBreakpointValues(true));

            // assert
            assertThat(eventProcessor.getBreakpointContexts(), is(empty()));

            RuntimeValue returnValue = eventProcessor.getReturnValues().get(0);
            assertThat(returnValue.getKind(), is(RuntimeValueKind.RETURN));
            assertThat(returnValue.getFields().size(), equalTo(1));

            FieldData field = returnValue.getFields().get(0);
            assertThat(field.getName(), equalTo("sides"));
            assertThat(field.getValue(), equalTo(3));
        }

        @Test
        void fieldInsideMultipleLevelNestedObjectShouldBeRecorded()
                throws AbsentInformationException, FileNotFoundException {
            // arrange
            String[] classpath =
                    TestHelper.getMavenClasspathFromBuildDirectory(
                            TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
            String[] tests = new String[] {"foo.ObjectsTest::maybeTwoMoreLevels"};
            File classesAndBreakpoints =
                    TestHelper.PATH_TO_INPUT
                            .resolve("objects")
                            .resolve("multiple-level-nesting.txt")
                            .toFile();

            // act
            EventProcessor eventProcessor =
                    Collector.invoke(
                            classpath,
                            tests,
                            classesAndBreakpoints,
                            setExecutionDepth(3).setSkipReturnValues(true));

            // assert
            assertThat(eventProcessor.getReturnValues(), is(empty()));

            RuntimeValue field =
                    eventProcessor
                            .getBreakpointContexts()
                            .get(0)
                            .getStackFrameContexts()
                            .get(0)
                            .getRuntimeValueCollection()
                            .get(0);

            RuntimeValue oneLevelDeep = field.getFields().get(0);
            assertThat(oneLevelDeep.getKind(), is(RuntimeValueKind.FIELD));
            assertThat(oneLevelDeep.getName(), equalTo("z"));

            RuntimeValue twoLevelsDeep = oneLevelDeep.getFields().get(0);
            assertThat(twoLevelsDeep.getKind(), is(RuntimeValueKind.FIELD));
            assertThat(twoLevelsDeep.getName(), equalTo("y"));

            RuntimeValue threeLevelsDeep = twoLevelsDeep.getFields().get(0);
            assertThat(threeLevelsDeep.getKind(), is(RuntimeValueKind.FIELD));
            assertThat(threeLevelsDeep.getName(), equalTo("x"));
            assertThat(threeLevelsDeep.getValue(), equalTo(42));
        }
    }

    @Test
    void getReadableValue_voidValueShouldBeRecorded()
            throws AbsentInformationException, FileNotFoundException {
        // arrange
        String[] classpath =
                TestHelper.getMavenClasspathFromBuildDirectory(
                        TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
        String[] tests = new String[] {"foo.VoidMethodTest::test_doNothing"};
        File classesAndBreakpoints = TestHelper.PATH_TO_INPUT.resolve("void-method.txt").toFile();
        // act
        EventProcessor eventProcessor =
                Collector.invoke(
                        classpath,
                        tests,
                        classesAndBreakpoints,
                        TestHelper.getDefaultOptions().setSkipBreakpointValues(true));

        // assert
        assertThat(eventProcessor.getBreakpointContexts(), is(empty()));
        RuntimeValue returnValue = eventProcessor.getReturnValues().get(0);
        assertThat(returnValue.getValue(), equalTo("<void value>"));
    }

    @Test
    void collectVariables_variablesInsideAnonymousClassShouldBeCollected()
            throws FileNotFoundException, AbsentInformationException {
        // arrange
        String[] classpath =
                TestHelper.getMavenClasspathFromBuildDirectory(
                        TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
        String[] tests = new String[] {"foo.AnonymousTest::test_implementAnonymousGreetings"};
        File classesAndBreakpoints =
                TestHelper.PATH_TO_INPUT.resolve("anonymous-class.txt").toFile();

        // act
        EventProcessor eventProcessor =
                Collector.invoke(
                        classpath, tests, classesAndBreakpoints, TestHelper.getDefaultOptions());

        // assert
        assertThat(eventProcessor.getReturnValues(), is(empty()));

        RuntimeValue hindiGreeting =
                eventProcessor
                        .getBreakpointContexts()
                        .get(0)
                        .getStackFrameContexts()
                        .get(0)
                        .getRuntimeValueCollection()
                        .get(0);
        assertThat(hindiGreeting.getKind(), is(RuntimeValueKind.LOCAL_VARIABLE));
        assertThat(hindiGreeting.getValue(), equalTo("Namaste"));

        RuntimeValue swedishGreeting =
                eventProcessor
                        .getBreakpointContexts()
                        .get(1)
                        .getStackFrameContexts()
                        .get(0)
                        .getRuntimeValueCollection()
                        .get(0);
        assertThat(swedishGreeting.getKind(), is(RuntimeValueKind.LOCAL_VARIABLE));
        assertThat(swedishGreeting.getValue(), equalTo("Tjenare!"));
    }

    @Test
    void processMethodExit_returnOfOuterMethodShouldBeCollected_notLambda()
            throws AbsentInformationException, FileNotFoundException {
        // arrange
        String[] classpath =
                TestHelper.getMavenClasspathFromBuildDirectory(
                        TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
        String[] tests = new String[] {"foo.AnonymousTest::test_printString"};
        File classesAndBreakpoints = TestHelper.PATH_TO_INPUT.resolve("lambda.txt").toFile();

        // act
        EventProcessor eventProcessor =
                Collector.invoke(
                        classpath,
                        tests,
                        classesAndBreakpoints,
                        TestHelper.getDefaultOptions().setSkipBreakpointValues(true));

        assertThat(eventProcessor.getBreakpointContexts(), is(empty()));

        RuntimeValue returnValue = eventProcessor.getReturnValues().get(0);
        assertThat(returnValue.getKind(), is(RuntimeValueKind.RETURN));
        assertThat(returnValue.getValue(), equalTo("Hey!"));
    }

    @Nested
    class BreakPointAtEndCurlyBrace {
        @Test
        void nonVoidMethod_doesNotCollectAnything()
                throws FileNotFoundException, AbsentInformationException {
            // arrange
            String[] classpath =
                    TestHelper.getMavenClasspathFromBuildDirectory(
                            TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
            String[] tests =
                    new String[] {
                        "foo.BreakpointAtEndCurlyBraceTest::test_shouldEndLineBeCollected_nonVoid"
                    };
            File classesAndBreakpoints =
                    TestHelper.PATH_TO_INPUT
                            .resolve("breakpoint-at-end-curly-brace")
                            .resolve("non-void.txt")
                            .toFile();

            // act
            EventProcessor eventProcessor =
                    Collector.invoke(
                            classpath,
                            tests,
                            classesAndBreakpoints,
                            TestHelper.getDefaultOptions());

            // assert
            assertThat(eventProcessor.getBreakpointContexts(), is(empty()));
            assertThat(eventProcessor.getReturnValues(), is(empty()));
        }

        @Test
        void voidMethod_voidIsCollected() throws FileNotFoundException, AbsentInformationException {
            // arrange
            String[] classpath =
                    TestHelper.getMavenClasspathFromBuildDirectory(
                            TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
            String[] tests =
                    new String[] {
                        "foo.BreakpointAtEndCurlyBraceTest::test_doNotReturnAnything_void"
                    };
            File classesAndBreakpoints =
                    TestHelper.PATH_TO_INPUT
                            .resolve("breakpoint-at-end-curly-brace")
                            .resolve("void.txt")
                            .toFile();

            // act
            EventProcessor eventProcessor =
                    Collector.invoke(
                            classpath,
                            tests,
                            classesAndBreakpoints,
                            TestHelper.getDefaultOptions());

            // assert
            List<RuntimeValue> runtimeValuesFromBreakpoint =
                    eventProcessor
                            .getBreakpointContexts()
                            .get(0)
                            .getStackFrameContexts()
                            .get(0)
                            .getRuntimeValueCollection();
            assertThat(runtimeValuesFromBreakpoint, is(empty()));

            RuntimeValue returnValue = eventProcessor.getReturnValues().get(0);
            assertThat(returnValue.getKind(), is(RuntimeValueKind.RETURN));
            assertThat(returnValue.getValue(), equalTo("<void value>"));
        }
    }

    @Test
    void dataShouldBeCollectedInsideSwitchBlock()
            throws FileNotFoundException, AbsentInformationException {
        // arrange
        String[] classpath =
                TestHelper.getMavenClasspathFromBuildDirectory(
                        TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
        String[] tests = new String[] {"foo.SwitchCaseTest::test"};
        File classesAndBreakpoints = TestHelper.PATH_TO_INPUT.resolve("switch-case.json").toFile();

        // act
        EventProcessor eventProcessor =
                Collector.invoke(
                        classpath,
                        tests,
                        classesAndBreakpoints,
                        TestHelper.getDefaultOptions().setSkipBreakpointValues(true));

        // assert
        assertThat(eventProcessor.getReturnValues().size(), equalTo(8));
    }

    @Test
    void returnDataShouldBeCollected_evenIfThereAreNoBreakpoints()
            throws FileNotFoundException, AbsentInformationException {
        // arrange
        String[] classpath =
                TestHelper.getMavenClasspathFromBuildDirectory(
                        TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
        String[] tests = new String[] {"foo.RecordMyReturnButWithoutBreakpointsTest::abba"};
        File classesAndBreakpoints =
                TestHelper.PATH_TO_INPUT
                        .resolve("return-value-without-breakpoints")
                        .resolve("input.txt")
                        .toFile();
        File methodsForExitEvent =
                TestHelper.PATH_TO_INPUT
                        .resolve("return-value-without-breakpoints")
                        .resolve("methods.json")
                        .toFile();

        // act
        EventProcessor eventProcessor =
                Collector.invoke(
                        classpath,
                        tests,
                        classesAndBreakpoints,
                        methodsForExitEvent,
                        TestHelper.getDefaultOptions().setSkipBreakpointValues(true));

        // assert
        assertThat(eventProcessor.getReturnValues().size(), equalTo(1));
    }

    @Test
    void recordReturnValueOfMethod_ifFullyQualifiedName_andMethodName_matches()
            throws FileNotFoundException, AbsentInformationException {
        // arrange
        String[] classpath =
                TestHelper.getMavenClasspathFromBuildDirectory(
                        TestHelper.PATH_TO_SAMPLE_MAVEN_PROJECT.resolve("with-debug"));
        String[] tests = new String[] {"foo.TwinsTest::executeBothMethods"};
        File classesAndBreakpoints =
                TestHelper.PATH_TO_INPUT.resolve("twins").resolve("input.txt").toFile();
        File methodsForExitEvent =
                TestHelper.PATH_TO_INPUT.resolve("twins").resolve("methods.json").toFile();

        // act
        EventProcessor eventProcessor =
                Collector.invoke(
                        classpath,
                        tests,
                        classesAndBreakpoints,
                        methodsForExitEvent,
                        TestHelper.getDefaultOptions());

        // assert
        // return value from foo.twin.B#getValue is ignored
        assertThat(eventProcessor.getReturnValues().size(), equalTo(1));
        ReturnData returnData = eventProcessor.getReturnValues().get(0);

        assertThat(returnData.getValue(), equalTo("a"));
    }

//    @Disabled("Shall be fixed after #86")
    @Test
    void dataShouldBeCollectedAtSpecifiedBreakpoint()
            throws FileNotFoundException, AbsentInformationException {
        // arrange
        Path pathToFlakyTests = Paths.get("src/test/resources/flaky-tests");
        Path pathToCommonsLang = pathToFlakyTests.resolve("commons-lang");
        String[] classpath =
                TestHelper.getMavenClasspathFromBuildDirectory(pathToCommonsLang.resolve("target"));
        String[] tests = new String[] {"org.apache.commons.lang3.text.WordUtilsTest::testLANG1397"};
        File classesAndBreakpoints = pathToFlakyTests.resolve("input.txt").toFile();

        // act
        EventProcessor eventProcessor =
                Collector.invoke(
                        classpath,
                        tests,
                        classesAndBreakpoints,
                        null,
                        TestHelper.getDefaultOptions());

        // assert
        assertThat(eventProcessor.getBreakpointContexts().size(), equalTo(1));
        BreakPointContext breakPoint = eventProcessor.getBreakpointContexts().get(0);
        assertThat(breakPoint.getLineNumber(), equalTo(272));
    }
}
