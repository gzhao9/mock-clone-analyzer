# Mock Clone Analyzer

Mock Clone Analyzer is a command-line tool for analyzing Mockito-based mock usage in Java test code and detecting mock clones.

## Requirements

- Java 17 or higher  
- Maven 3.6 or higher  
- Gradle 6.0 or higher

> **Note:**  
> Mock Clone Analyzer relies on Maven or Gradle for dependency management and source processing. The analyzed Java project does not need to compile successfully, but it must have a complete dependency configuration (such as `pom.xml` for Maven or `build.gradle` for Gradle). Please ensure the target project is properly configured and uses Maven 3.6+ or Gradle 6.0+ so that commands like `mvn compile` or `gradle build` can resolve dependencies and generate sources. Otherwise, the analysis may fail.

## Build Instructions

To compile the project and package it into an executable JAR file:

```bash
mvn clean package
```

The assembled JAR will be located at:

```
target/mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Usage

```bash
java -jar mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar <command> <args...>
```

### Export Mock Information

Analyze a Java project and export raw mock information, including mock creation and usage metadata:

```bash
java -jar mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar info <projectRoot> <outputFile> [--skip]
```

- `<projectRoot>`: Path to the root directory of the Java project to analyze  
- `<outputFile>`: Path to the output JSON file (e.g., `mockinfo.json`)  
- `--skip` (optional): Skips the build step (`mvn compile` or `gradle build`)  

### Export Mock Sequences

This mode outputs abstracted mock sequences used in test cases, preparing input for clone detection:

```bash
java -jar mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar sequence <projectRoot> <outputFile> [--skip]
```

- The output file (e.g., `sequences.json`) contains a list of all mock usage sequences with test context and abstracted statements.

### Detect Mock Clones

> **Want to know how clone detection works?**  
> See [Clonal detection algorithm](Clonal%20detection%20algorithm.md) for the detailed algorithm, examples, and LOC-saving strategy used by the mock clone analyzer.

Run clone detection in one of the following ways:

**1. Directly from a project directory:**

```bash
java -jar mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar clone <projectRoot> <outputCloneFile> [--skip]
```

**2. From an existing mockinfo.json file:**  
(Currently not supported via CLI, but can be integrated manually using `MockCloneDetector` API.)

## Example

```bash
java -jar target/mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar info "C:\java tool\Apache\dubbo" "output/dubbo.json"
java -jar target/mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar sequence "C:\java tool\Apache\dubbo" "output/dubbo-sequences.json"
java -jar target/mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar clone "C:\java tool\Apache\dubbo" "output/dubbo-clone.json"
```

## Output Files

- `mockinfo.json`: Raw metadata for all mock objects, including declaration and usage context  
- `sequences.json`: Abstracted mock usage sequences grouped by test case  
- `clone.json`: Detected mock clone groups, with reusable mock patterns and LOC savings estimates
