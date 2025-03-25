```markdown
# Mock Clone Analyzer

Mock Clone Analyzer is a command-line tool for analyzing Mockito-based mock usage in Java test code and detecting mock clones.

## Requirements

- Java 17 or higher
- Maven 3.6 or higher

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

This mode analyzes a Java project and exports mock-related information in JSON format.

```bash
java -jar mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar info <projectRoot> <outputFile> [--skip]
```

- `<projectRoot>`: Path to the root directory of the Java project to analyze.
- `<outputFile>`: Path to the output JSON file.
- `--skip` (optional): Skips the build step (`mvn compile` or `gradle build`). Recommended after the first run if the symbol resolver has already been initialized.


### Detect Mock Clones

You can run clone detection in two ways:

**1. Using an existing mockinfo.json file:**

```bash
java -jar mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar clone <mockinfo.json> <outputCloneFile>
```

**2. Directly from a project directory:**

```bash
java -jar mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar clone <projectRoot> <outputCloneFile> [--skip]
```

## Example

```bash
java -jar target/mock-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar info "C:\java tool\Apache\dubbo" "output/dubbo.json"
```

## Output Files

- The mock info file (`mockinfo.json`) contains detailed metadata about mock declarations, usage, and stubbing/verification patterns.
- The clone result file (`clone.json`) contains detected clone groups among mock usages.

```
