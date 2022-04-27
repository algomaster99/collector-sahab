package se.kth.debug;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import se.kth.debug.struct.FileAndBreakpoint;
import se.kth.debug.struct.MethodForExitEvent;
import se.kth.debug.struct.result.BreakPointContext;
import se.kth.debug.struct.result.ReturnData;
import se.kth.debug.struct.result.StackFrameContext;

/** For managing events triggered by JDB. */
public class EventProcessor {
    private static final Logger logger = Logger.getLogger(EventProcessor.class.getName());

    private static final int TIMEOUT = 5000; // milliseconds;
    private final List<BreakPointContext> breakpointContexts = new ArrayList<>();
    private final List<ReturnData> returnValues = new ArrayList<>();
    private final Debugger debugger;

    private boolean shouldRecordBreakpointData = false;
    private boolean shouldRecordReturnData = false;

    EventProcessor(
            String[] providedClasspath,
            String[] tests,
            File classesAndBreakpoints,
            File methodsForExitEvent) {
        parseMethodsForExitEvent(methodsForExitEvent);
        shouldRecordBreakpointData = classesAndBreakpoints != null;
        shouldRecordReturnData = methodsForExitEvent != null;
        debugger =
                new Debugger(
                        providedClasspath,
                        tests,
                        parseFileAndBreakpoints(classesAndBreakpoints),
                        parseMethodsForExitEvent(methodsForExitEvent));
    }

    /** Monitor events triggered by JDB. */
    public void startEventProcessor(CollectorOptions context) throws AbsentInformationException {
        VirtualMachine vm = debugger.launchVMAndJunit();
        debugger.addClassPrepareEvent(vm);
        vm.resume();
        try {
            EventSet eventSet;
            while ((eventSet = vm.eventQueue().remove()) != null) {
                for (Event event : eventSet) {
                    if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                        debugger.getProcess().destroy();
                    }
                    if (event instanceof ClassPrepareEvent) {
                        if (shouldRecordBreakpointData) {
                            debugger.setBreakpoints(vm, (ClassPrepareEvent) event);
                        }
                        if (shouldRecordReturnData) {
                            debugger.registerMethodExits(vm, (ClassPrepareEvent) event);
                        }
                    }
                    if (event instanceof BreakpointEvent) {
                        List<StackFrameContext> result =
                                debugger.processBreakpoints((BreakpointEvent) event, context);
                        Location location = ((BreakpointEvent) event).location();
                        breakpointContexts.add(
                                new BreakPointContext(
                                        location.sourcePath(), location.lineNumber(), result));
                    }
                    if (event instanceof MethodExitEvent) {
                        ReturnData rd =
                                debugger.processMethodExit((MethodExitEvent) event, context);
                        if (rd != null) {
                            returnValues.add(rd);
                        }
                    }
                }
                vm.resume();
            }
        } catch (VMDisconnectedException | IncompatibleThreadStateException e) {
            logger.warning(e.toString());
        } catch (InterruptedException e) {
            logger.warning(e.toString());
            Thread.currentThread().interrupt();
        }
    }

    private List<FileAndBreakpoint> parseFileAndBreakpoints(File classesAndBreakpoints) {
        if (classesAndBreakpoints == null) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(classesAndBreakpoints))) {
            List<FileAndBreakpoint> parsedFileAndBreakpoints = new ArrayList<>();
            for (String line; (line = br.readLine()) != null; ) {
                String[] fileAndBreakpoints = line.split("=");
                String[] breakpoints = fileAndBreakpoints[1].split(",");
                List<Integer> parsedBreakpoints =
                        Arrays.stream(breakpoints)
                                .map(Integer::parseInt)
                                .collect(Collectors.toList());
                FileAndBreakpoint fNB =
                        new FileAndBreakpoint(fileAndBreakpoints[0], parsedBreakpoints);
                parsedFileAndBreakpoints.add(fNB);
            }
            return parsedFileAndBreakpoints;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private MethodForExitEvent parseMethodsForExitEvent(File methodsForExitEvent) {
        if (methodsForExitEvent == null) {
            return null;
        }
        try (JsonReader jr = new JsonReader(new FileReader(methodsForExitEvent))) {
            Gson gson = new Gson();
            return gson.fromJson(jr, MethodForExitEvent.class);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /** Returns the values corresponding to each breakpoint. */
    public List<BreakPointContext> getBreakpointContexts() {
        return breakpointContexts;
    }

    public List<ReturnData> getReturnValues() {
        return returnValues;
    }
}
