# Mock Clone Analyzer

A tool for detecting and refactoring duplicate mock object usage patterns in Java test code.

## What are Mock Clones?

Mock clones are duplicate mock object configurations that appear across different test cases. These duplications increase code maintenance burden and can lead to inconsistencies when tests need to be updated.

### Standard Mocking Pattern

A typical mock usage follows this pattern:

```java
// Creation - Create a mock object
Service mockService = mock(Service.class);

// Stubbing - Configure the mock behavior
when(mockService.getData()).thenReturn("test data");
when(mockService.getStatus()).thenReturn(Status.ACTIVE);

// Test execution
systemUnderTest.process(mockService);

// Verification - Verify the mock was used as expected
verify(mockService).logAccess();
```

### The Problem: Duplicate Mock Configurations

Consider these two test methods:

```java
@Test
public void testProcess() {
    // Creation
    Service mockService = mock(Service.class);
    
    // Stubbing
    when(mockService.getData()).thenReturn("test data");
    when(mockService.getStatus()).thenReturn(Status.ACTIVE);
    
    systemUnderTest.process(mockService);
    verify(mockService).logAccess();
}

@Test
public void testAnalyze() {
    // Same mock creation and stubbing repeated
    Service mockService = mock(Service.class);
    when(mockService.getData()).thenReturn("test data");
    when(mockService.getStatus()).thenReturn(Status.ACTIVE);
    when(mockService.getMode()).thenReturn(Mode.ANALYSIS);
    
    systemUnderTest.analyze(mockService);
    verify(mockService).logAccess();
}
```

The mock creation and some stubbing code is duplicated, creating a "mock clone."

## Mock Sequence Analysis

To detect mock clones, we categorize mock usage statements into three operation locations:

1. **Creation Location** - Where mock objects are instantiated
2. **Stubbing Location** - Where mock behavior is configured
3. **Verification Location** - Where mock interactions are verified

Based on our analysis of six open-source projects, we found:

### Creation Location Distribution

| Location      | Count | Percentage |
|---------------|-------|------------|
| Test Case     | 6616  | 58%        |
| Attribute     | 4158  | 37%        |
| Helper Method | 136   | 1%         |
| @Before       | 96    | 1%         |

---

### Stubbing Location Distribution

| Location           | Count | Percentage |
|--------------------|-------|------------|
| None               | 5647  | 50%        |
| Test Case          | 4683  | 41%        |
| @Before            | 440   | 4%         |
| Helper Method      | 192   | 2%         |
| @Before + Test Case| 91    | 1%         |
| Other Methods      | 37    | 0%         |

---

### Verification Location Distribution

| Location      | Count | Percentage |
|---------------|-------|------------|
| None          | 9289  | 82%        |
| Test Case     | 1801  | 16%        |
| Other Methods | -     | 2%         |

Our analysis focuses exclusively on mock creation and stubbing operations because:

- Verification statements are test-specific and rarely reusable across tests
- 82% of mock sequences don't contain verification statements

This targeted approach maximizes refactoring opportunities where duplication commonly occurs.

## Mock Clone Detection Approach

### 1. Classification by Location

We categorize mock statements into two types:
- **Shareable mock lines**: Code in @Before methods, attributes, or helper methods
- **Test-specific mock lines**: Code within individual test methods

For example:
```java
public class ServiceTest {
    // Shareable (attribute)
    private Service mockService = mock(Service.class);

    @Before
    public void setup() {
        // Shareable (@Before)
        when(mockService.getEnvironment()).thenReturn("test");
    }

    @Test
    public void testProcess() {
        // Test-specific (local)
        when(mockService.getData()).thenReturn("test data");
        systemUnderTest.process(mockService);
    }

    @Test
    public void testAnalyze() {
        // Test-specific (local)
        when(mockService.getData()).thenReturn("test data");
        when(mockService.getMode()).thenReturn(Mode.ANALYSIS);
        systemUnderTest.analyze(mockService);
    }
}
```

For clone detection, we combine the shareable and test-specific lines for each test method:

For `testProcess()`:
- **Combined sequence:**
  ```java
  // shareableMockLines:
  private Service mockService = mock(Service.class);
  when(mockService.getEnvironment()).thenReturn("test");
  
  // testMockLines:
  when(mockService.getData()).thenReturn("test data");
  systemUnderTest.process(mockService); // (non-mock line)
  ```

For `testAnalyze()`:
- **Combined sequence:**
  ```java
  // shareableMockLines (same as above):
  private Service mockService = mock(Service.class);
  when(mockService.getEnvironment()).thenReturn("test");
  
  // testMockLines:
  when(mockService.getData()).thenReturn("test data");
  when(mockService.getMode()).thenReturn(Mode.ANALYSIS);
  systemUnderTest.analyze(mockService); // (non-mock line)
  ```

We then abstract these combinations for comparison, focusing only on the mock-related operations.


### 2. Sequence Abstraction

We abstract mock sequences to normalize their format regardless of the specific mocking style used:

```java
// Original code
Service mockService = mock(Service.class);
Mockito.when(mockService.getData()).thenReturn("test data");
when(mockService.getStatus()).thenReturn(Status.ACTIVE);

// Abstracted format
MockObject = {
    "when(org.example.Service.getData()).thenReturn(String)",
    "when(org.example.Service.getStatus()).thenReturn(Status)"
}
```

This abstraction normalizes different mocking styles to the same representation. For example:

```java
// These different syntaxes:
Mockito.when(moduleDeployer.isRunning()).thenReturn(true);
given(mockDep.isRunning()).willReturn(false);

// Are both abstracted to the same representation:
"when(org.apache.dubbo.common.deploy.ModuleDeployer.isRunning()).thenReturn(boolean)"
```

This allows us to identify equivalent stubbing operations regardless of the specific syntax used or literal values returned.

### 3. Common Pattern Detection

For test cases that mock the same class, we represent each mock as a set of stubbing operations:

```
MockObject1 = {s1, s2, s3}      // s1, s2, s3 are different stubbing operations
MockObject2 = {s2, s3, s4}
MockObject3 = {s2, s4, s5}
```

We identify common subsets of stubbing operations that appear across multiple tests.

## Clone Refactoring Strategies

Based on the patterns we detect, we suggest two main refactoring strategies:

### 1. Reusable Helper Methods for Common Stubbing Patterns

When we find common stubbing patterns, we recommend extracting them to helper methods:

**Before refactoring:**
```java
@Test
public void test1() {
    Service mockService = mock(Service.class);
    when(mockService.getData()).thenReturn("data");
    when(mockService.getStatus()).thenReturn(Status.ACTIVE);
    when(mockService.getOwner()).thenReturn("user1");
    // test code
}

@Test
public void test2() {
    Service mockService = mock(Service.class);
    when(mockService.getData()).thenReturn("data");
    when(mockService.getStatus()).thenReturn(Status.ACTIVE);
    when(mockService.getPriority()).thenReturn(1);
    // different test code
}
```

**After refactoring:**
```java
// Helper method for common stubbing
private Service createBasicMockService() {
    Service mockService = mock(Service.class);
    when(mockService.getData()).thenReturn("data");
    when(mockService.getStatus()).thenReturn(Status.ACTIVE);
    return mockService;
}

@Test
public void test1() {
    Service mockService = createBasicMockService();
    when(mockService.getOwner()).thenReturn("user1");
    // test code
}

@Test
public void test2() {
    Service mockService = createBasicMockService();
    when(mockService.getPriority()).thenReturn(1);
    // different test code
}
```

### 2. @Before Method for Common Mock Creations

For tests that only create the same mocks without stubbing, we recommend moving creation to @Before:

**Before refactoring:**
```java
@Test
public void test1() {
    Service mockService = mock(Service.class);
    Client mockClient = mock(Client.class);
    // test code
}

@Test
public void test2() {
    Service mockService = mock(Service.class);
    Client mockClient = mock(Client.class);
    // different test code
}
```

**After refactoring:**
```java
private Service mockService;
private Client mockClient;

@Before
public void setup() {
    mockService = mock(Service.class);
    mockClient = mock(Client.class);
}

@Test
public void test1() {
    // test code using mockService and mockClient
}

@Test
public void test2() {
    // different test code using mockService and mockClient
}
```

## Optimizing Refactoring with Pattern Mining

For complex cases with multiple overlapping patterns, we use a combination of the Apriori algorithm and a greedy approach to find optimal refactoring opportunities.

Consider this example with multiple mock objects and stubbing patterns:

```
MockObject1 = {"getData()", "getStatus()", "getOwner()"}
MockObject2 = {"getStatus()", "getOwner()", "getPriority()"}
MockObject3 = {"getStatus()", "getPriority()", "getType()"}
MockObject4 = {"getData()", "getPriority()"}
MockObject5 = {"getData()", "getEnvironment()"}
```

The algorithm identifies these optimal patterns to extract:
1. Pattern A = {"getData()"} → appears in objects 1, 4, 5
2. Pattern B = {"getStatus()", "getPriority()"} → appears in objects 2, 3

This minimizes code duplication while maintaining test clarity.

## Using the Analyzer

The Mock Clone Analyzer tool automates this process:

1. Analyzes Java test code to extract mock usage
2. Abstracts mock sequences into comparable patterns
3. Identifies common patterns using sequence mining
4. Suggests optimal refactoring strategies
5. Estimates lines of code that can be saved

For detailed usage instructions, see the [Tool Usage](#requirements) section above.

## Evaluation Results

We evaluated our clone detection algorithm on two major open-source Java projects:

| Project | New Algorithm Instances | Original Algorithm Instances | Improvement |
|---------|-------------------------|------------------------------|-------------|
| Apache Dubbo | 126 | 57 | 121% |
| Apache CloudStack | 1,374 | 447 | 207% |

The significant improvement in clone detection demonstrates the effectiveness of our refined approach in identifying more refactoring opportunities across diverse codebases.
