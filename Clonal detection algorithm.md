


# Mock Clones in Unit Tests

### Definition
**Mock clones** are repeated mock creation and stubbing code patterns across test methods. They represent a special form of code duplication unique to test code.

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

### Problem
- Test maintenance becomes costly as changes must be applied to all clones
- Traditional clone detectors typically miss these patterns
- Tests with partial mock setup differences (some methods stub `doC`, others don't) create **partial mock clones**

### Solution
We propose an algorithm that:
1. Detects partial mock clones by identifying frequent sub-sequences in mock setup code
2. Applies a greedy optimization process to maximize code reuse
3. Automatically refactors the clones into reusable test fixtures

This approach reduces test maintenance costs and improves test readability.

### Preprocessing Steps

Before applying the algorithm, we perform two preprocessing steps:

#### 1. Categorizing Mock Statements

Mock statements are classified into two types:
- **Shareable mock lines**: Code in `@Before` methods, attributes, or helper methods.
- **Test-specific mock lines**: Code within individual test methods.

Example:
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

For clone detection, we combine shareable and test-specific lines for each test method. For example:

- **`testProcess()` combined sequence:**
  ```java
  private Service mockService = mock(Service.class);
  when(mockService.getEnvironment()).thenReturn("test");
  when(mockService.getData()).thenReturn("test data");
  systemUnderTest.process(mockService); // (non-mock line)
  ```

- **`testAnalyze()` combined sequence:**
  ```java
  private Service mockService = mock(Service.class);
  when(mockService.getEnvironment()).thenReturn("test");
  when(mockService.getData()).thenReturn("test data");
  when(mockService.getMode()).thenReturn(Mode.ANALYSIS);
  systemUnderTest.analyze(mockService); // (non-mock line)
  ```

#### 2. Abstracting Mock Sequences

Mocking styles are normalized into a unified representation for comparison. For example:

```java
// Different syntaxes:
Mockito.when(moduleDeployer.isRunning()).thenReturn(true);
given(mockDep.isRunning()).willReturn(false);

// Abstracted representation:
"when(org.apache.dubbo.common.deploy.ModuleDeployer.isRunning()).thenReturn(boolean)"
```

This abstraction ensures consistent comparison across different mocking styles.

# Mock Clone Detection Algorithm 

### Step 1: Collect Mock Sequences

In each test method, we collect the mock creation and stubbing statements for every mock object and represent them as a mock sequence:


For a more complex mock, the sequence might look like this:
```java
// Mock sequence: Mi = {s1, s2, ..., sk}
MyService service = Mockito.mock(MyService.class); // Mock creation
when(service.doA("A")).thenReturn("resultA");      // s1
when(service.doB("B")).thenReturn("resultB");      // s2
when(service.doC("C")).thenReturn("resultC");      // s3
//....
when(service.doD("D")).thenReturn("resultD");      // sk
```

```text
Mi = {s1, s2, ..., sk}
```

For example, two tests may contain the following mock setup sequences:

- `M1 = {s1, s2, s3}`
- `M2 = {s2, s3, s4}`

Each `si` is an abstracted statement such as:

```java
s2: when(s.doA("A")).thenReturn("resultA");
s3: when(s.doB("B")).thenReturn("resultB");
```

In this case, the sub-sequence `{s2, s3}` is shared between `M1` and `M2`.

**Verification statements are excluded**, as they tend to be assertion-specific and do not reflect reusable setup logic.

---

### Step 2: Mine Frequent Sub-sequences

We apply frequent pattern mining (e.g., **Apriori** or **FP-Growth**) to the collection `M = {M1, M2, ..., Mn}` and extract repeated sub-sequences:

- In the example above, `E1 = {s2, s3}` would be identified as a frequent sub-sequence appearing in both `M1` and `M2`.

Such repeated sub-sequences can then be extracted into a shared helper method:

```java
private MyService getBaseMock() {
    MyService s = Mockito.mock(MyService.class);
    when(s.doA("A")).thenReturn("resultA");
    when(s.doB("B")).thenReturn("resultB");
    return s;
}
```

Each test can call this helper and add its own test-specific stubs as needed.



### Step 3: Sort Candidates by Potential Benefit

- Sort each `Ej` by the potential LOC savings:
  ```
  Priority Score = |Ej| × (|C(Ej)| - 1)
  ```
  where:
  - `|Ej|`: length of the sub-sequence (number of statements),
  - `|C(Ej)|`: number of mock sequences it covers,
  - Subtracting 1 accounts for the fact that only repeated occurrences reduce duplication.

---

### Step 4: Greedy Assignment of Mock Sequences

We apply a greedy strategy to assign each mock sequence to the best-matching sub-sequence, based on local priority, without considering long-term or global effects. This makes the algorithm efficient but potentially suboptimal, which is later addressed via multiple runs.

- Initialize all `Mi` as **unassigned**.
- For each `Mi`, do:
  1. Find all `Ej` such that `Ej ⊆ Mi` (i.e., `Ej` is a subset of `Mi`)
  2. If only one `Ej` matches, assign `Mi` to it.
  3. If multiple `Ej` match and have equal or similar priority, **break ties randomly**.
  4. Mark `Mi` as assigned once it’s covered.

---

### Step 5: Dynamic Cleanup and Reassignment

- For each `Ej`:
  - If `|C(Ej)| = 1` (covers only one `Mi`), **remove** it — it doesn’t reduce duplication.
  - Mark the corresponding `Mi` as **unassigned**.
- Go back to **Step 4** and reassign released sequences.
- Repeat until no more low-coverage `Ej` exist — i.e., the process has **converged**.

---

### Step 6: Compute LOC Savings

- Once assignments are stable, compute total saved lines of code:
  ```
  LOC_Reduction = ∑ [ |Ej| × (|C(Ej)| - 1) ]
  ```
  This measures how many lines can be factored out across shared mock setups.

---

### Step 7: Multiple Runs to Avoid Local Optima

- Because tie-breaks are random, different runs may yield different results.
- Run the algorithm **multiple times** (e.g., 100 iterations), each with a different random seed.
- Select the run that achieves the **highest total LOC Reduction** — this approximates the global optimum and avoids being stuck in suboptimal assignment paths.

---
## Clone Refactoring Strategies

Based on the mock clone instance we detect, we suggest two main refactoring strategies:

### 1. Reusable Helper Methods for Frequent Sub-sequences

When we find common stubbing Frequent Sub-sequences, we recommend extracting them to helper methods:

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
The  Frequent Sub-sequences is:
```java
    when(mockService.getData()).thenReturn("data");
    when(mockService.getStatus()).thenReturn(Status.ACTIVE);
    when(mockService.getPriority()).thenReturn(1);
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

### Output Example

The provided JSON output demonstrates an example where three shared statements and one non-shared sequence are transformed into a structure where all four stub statements are shared. This transformation is achieved by abstracting the mock and stub statements into a generalized format, enabling reuse across different test cases. The example highlights the following key aspects:

1. **Shared Statements**: These are the mock or stub statements that are common across multiple test cases. In this example, the shared statements include:
  - `when(Directory<DemoService>.getConsumerUrl()).thenReturn(org.apache.dubbo.common.URL)`
  - `when(Directory<DemoService>.getInterface()).thenReturn(Directory<DemoService>)`
  - `when(Directory<DemoService>.getUrl()).thenReturn(org.apache.dubbo.common.URL)`

2. **Non-Shared Sequence**: Initially, one sequence contains a unique stub statement:
  - `when(Directory<DemoService>.list(org.apache.dubbo.rpc.RpcInvocation)).thenReturn(java.util.List<org.apache.dubbo.rpc.cluster.support.MockInvoker>)`

3. **Transformation**: By abstracting the unique stub statement and integrating it into the shared structure, the output ensures that all four stub statements are now shared, improving maintainability and reducing redundancy in the test code.

This example illustrates the process of optimizing test code by consolidating shared behaviors, which can be particularly useful in large-scale projects with extensive test suites.

```json
{
"mockedClass": "Directory<DemoService>",
"packageName": "org.apache.dubbo.rpc.cluster.support",
"testCaseCount": 3,
"sequenceCount": 6,
"sharedStatementLineCount": 4,
"locReduced": 20,
"sharedStatements": [
  "when(Directory<DemoService>.getConsumerUrl()).thenReturn(org.apache.dubbo.common.URL)",
  "when(Directory<DemoService>.getInterface()).thenReturn(Directory<DemoService>)",
  "when(Directory<DemoService>.getUrl()).thenReturn(org.apache.dubbo.common.URL)",
  "when(Directory<DemoService>.list(org.apache.dubbo.rpc.RpcInvocation)).thenReturn(java.util.List<org.apache.dubbo.rpc.cluster.support.MockInvoker>)"
],
"sequences": [
  {
    "variableName": "dic",
    "variableType": "Directory<DemoService>",
    "mockedClass": "Directory<DemoService>",
    "packageName": "org.apache.dubbo.rpc.cluster.support",
    "filePath": "C:\\java tool\\Apache\\dubbo\\dubbo-cluster\\src\\test\\java\\org\\apache\\dubbo\\rpc\\cluster\\support\\BroadCastClusterInvokerTest.java",
    "className": "BroadCastClusterInvokerTest",
    "testMethodName": "testNormal",
    "overlapLines": [],
    "shareableMockLines": {
      "44": "private Directory<DemoService> dic;",
      "56": "dic = mock(Directory.class);",
      "64": "given(dic.getUrl()).willReturn(url);",
      "65": "given(dic.getConsumerUrl()).willReturn(url);",
      "66": "given(dic.getInterface()).willReturn(DemoService.class);"
    },
    "testMockLines": {
      "76": "given(dic.list(invocation)).willReturn(Arrays.asList(invoker1, invoker2, invoker3, invoker4));"
    },
    "abstractedStatement": {
      "64": "when(Directory<DemoService>.getUrl()).thenReturn(org.apache.dubbo.common.URL)",
      "65": "when(Directory<DemoService>.getConsumerUrl()).thenReturn(org.apache.dubbo.common.URL)",
      "66": "when(Directory<DemoService>.getInterface()).thenReturn(Directory<DemoService>)",
      "76": "when(Directory<DemoService>.list(org.apache.dubbo.rpc.RpcInvocation)).thenReturn(java.util.List<org.apache.dubbo.rpc.cluster.support.MockInvoker>)"
    }
  },
  {
    "variableName": "dic",
    "variableType": "Directory<DemoService>",
    "mockedClass": "Directory<DemoService>",
    "packageName": "org.apache.dubbo.rpc.cluster.support",
    "filePath": "C:\\java tool\\Apache\\dubbo\\dubbo-cluster\\src\\test\\java\\org\\apache\\dubbo\\rpc\\cluster\\support\\BroadCastClusterInvokerTest.java",
    "className": "BroadCastClusterInvokerTest",
    "testMethodName": "testEx",
    "overlapLines": [],
    "shareableMockLines": {
      "44": "private Directory<DemoService> dic;",
      "56": "dic = mock(Directory.class);",
      "64": "given(dic.getUrl()).willReturn(url);",
      "65": "given(dic.getConsumerUrl()).willReturn(url);",
      "66": "given(dic.getInterface()).willReturn(DemoService.class);"
    },
    "testMockLines": {
      "87": "given(dic.list(invocation)).willReturn(Arrays.asList(invoker1, invoker2, invoker3, invoker4));"
    },
    "abstractedStatement": {
      "64": "when(Directory<DemoService>.getUrl()).thenReturn(org.apache.dubbo.common.URL)",
      "65": "when(Directory<DemoService>.getConsumerUrl()).thenReturn(org.apache.dubbo.common.URL)",
      "66": "when(Directory<DemoService>.getInterface()).thenReturn(Directory<DemoService>)",
      "87": "when(Directory<DemoService>.list(org.apache.dubbo.rpc.RpcInvocation)).thenReturn(java.util.List<org.apache.dubbo.rpc.cluster.support.MockInvoker>)"
    }
    }
]
}
```
