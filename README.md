# Collector Sahab

[![GHA tests Workflow Status](https://github.com/algomaster99/collector-sahab/actions/workflows/tests.yml/badge.svg)](https://github.com/algomaster99/collector-sahab/actions/workflows/tests.yml)
[![Code Coverage](https://codecov.io/gh/algomaster99/collector-sahab/branch/main/graph/badge.svg)](https://codecov.io/gh/algomaster99/collector-sahab)

CLI to collect runtime context of a Java class.

## Execution

### Setup project for collecting runtime statistics

The exact steps varies for each project because of multiple development
pipelines possible. However, the output from this step is same. In other
words, you need the following things to proceed to next step. It does not
matter how you get them.

1. Compiled classes of the project with debug (`-g`) set to `true`.
2. Compiled test classes and its resources bundled. Debugging can be on or off
   depending upon how much data you want.
3. Dependencies of the project as compiled classes. It is recommended to keep
   debugging off for this because you will get a lot of data otherwise!

There are two ways to achieve this:
1. Create JAR-with-dependencies of the project with test classes bundled too.
2. Use commands provided by maven plugins.
   1. `mvn compile`: for compiling classes
   2. `mvn test-compile`: for compiling test classes.
   3. `mvn dependency:copy-dependencies`: for copying dependencies in build folder.

### Build `collector-sahab`

1. Package the app
    ```bash
   $ mvn package
    ```
2. Prepare for execution of `collector sahab`.
   1. Create an `input` file containing a list of class names and breakpoints.
      Example:
      ```json
      [
        {
          "fileName": "ch.hsr.geohash.BoundingBox",
          "breakpoints": [210]
        },
        {
          "fileName": "ch.hsr.geohash.util.DoubleUtil",
          "breakpoints": [12, 15]
        }
      ]
      ```
   2. Run the process
      ```bash
      $ java -jar/target/collector-sahab-1.0-SNAPSHOT-jar-with-dependencies.jar \
           -p [path/to/all/classes/required ...]
           -t [classname::testMethod ...]
           -i <path/to/input/breakpoint/file>
           -o <path/to/output>
           --execution-depth (default=0)
           --number-of-array-elements (default=10)
           --skip-printing-field (default=false)
           --stack-trace-depth (default=0)
           --skip-breakpoint-values (default=false)
           --skip-return-values (default=false)
      ```
   3. Example output
   
   ```json
   {
      "breakpoint": [
            {
               "file": "foo/BasicMath.java",
               "lineNumber": 5,
               "stackFrameContexts": [
               {
                  "positionFromTopInStackTrace": 1,
                  "location": "foo.BasicMath:5",
                  "stackTrace": [
                     "add:5, foo.BasicMath",
                     "test_add:11, foo.BasicMathTest",
                     "runTest:40, se.kth.debug.JUnitTestRunner",
                     "lambda$main$0:16, se.kth.debug.JUnitTestRunner",
                     "call:-1, se.kth.debug.JUnitTestRunner$$Lambda$1.81628611"
                  ],
                  "runtimeValueCollection": [
                     {
                        "kind": "LOCAL_VARIABLE",
                        "name": "x",
                        "type": "int",
                        "value": 23,
                        "fields": null,
                        "arrayElements": null
                     },
                     {  
                        "kind": "LOCAL_VARIABLE",
                        "name": "y",
                        "type": "int",
                        "value": 2,
                        "fields": null,
                        "arrayElements": null
                     }
                  ]
               }
            ]
         },
         {
            "file": "foo/BasicMath.java",
            "lineNumber": 9,
            "stackFrameContexts": [
               {
                  "positionFromTopInStackTrace": 1,
                  "location": "foo.BasicMath:9",
                  "stackTrace": [
                     "subtract:9, foo.BasicMath",
                     "test_subtract:16, foo.BasicMathTest",
                     "runTest:40, se.kth.debug.JUnitTestRunner",
                     "lambda$main$0:16, se.kth.debug.JUnitTestRunner",
                     "call:-1, se.kth.debug.JUnitTestRunner$$Lambda$1.81628611"
                  ],
                  "runtimeValueCollection": [
                     {
                        "kind": "LOCAL_VARIABLE",
                        "name": "x",
                        "type": "int",
                        "value": 2,
                        "fields": null,
                        "arrayElements": null
                     },
                     {
                        "kind": "LOCAL_VARIABLE",
                        "name": "y",
                        "type": "int",
                        "value": 1,
                        "fields": null,
                        "arrayElements": null
                     } 
                  ]
               }
            ]
         }
      ]
   }
   ```

## Scripts

### MatchedLineFinder

It takes in exactly four arguments in the specified order:
1. **Absolute path to project** whose runtime data needs to be collected.
2. **Filename** of the class where the patch is.
3. **Left** commit, or any reference acceptable by `git checkout`.
4. **Right** commit, or any reference acceptable by `git checkout`.

> "or any reference acceptable by `git checkout`" is not tested for.

**Example execution**
```bash
java -cp target/collector-sahab-1.0-SNAPSHOT-jar-with-dependencies.jar se.kth.debug.MatchedLineFinder /home/assert/Desktop/experiments/drr-as-pr/Time-11 DateTimeZoneBuilder.java e5d67a8162aebb7dbd5df8cdc21442ef111d2ba1 1c04679173a46faa59e73f68def33f60843f8beb
```

> Runs as a part of `script/bribe-sahab.py`

### scripts/bribe-sahab.py

It does three things:
1. Compiles target project for both revisions
2. Find the matched lines
3. Run collector-sahab over the project to collect runtime context

Before you run `bribe-sahab`, populate config file by following the steps below:
1. ```
   cp config_stencil.json config.json
   ```
2. Fill `config.json` accordingly. See example:
   ```json
   {
     "collectorJar": "/home/assert/Desktop/assert-achievements/collector-sahab/target/collector-sahab-0.0.1-SNAPSHOT-jar-with-dependencies.jar",
     "outputDirectory": "/home/assert/Desktop/experiments/drr-as-pr/"
   }
   ```


```bash
usage: Bribe sahab [-h] -p PROJECT -l LEFT -r RIGHT -c CLASS_FILENAME -t TESTS [TESTS ...]

optional arguments:
  -h, --help            show this help message and exit
  -p PROJECT, --project PROJECT
                        Path to project
  -l LEFT, --left LEFT  Left revision
  -r RIGHT, --right RIGHT
                        Right revision
  -c CLASS_FILENAME, --class-filename CLASS_FILENAME
                        Name of the file which contains the patch
  -t TESTS [TESTS ...], --tests TESTS [TESTS ...]
                        Tests executed by collector sahab
```
