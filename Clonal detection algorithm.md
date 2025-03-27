


# Mock Clones in Unit Tests

### Definition
**Mock clones** are repeated mock creation and stubbing code patterns across test methods. They represent a special form of code duplication unique to test code.

### Example
```java
// Appears in multiple test methods
MyService service = Mockito.mock(MyService.class);
when(service.doA("A")).thenReturn("resultA");
when(service.doB("B")).thenReturn("resultB");
```

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


# Mock Clone Detection Algorithm 

### Step 1: Collect Mock Sequences

In each test method, we collect the mock creation and stubbing statements for every mock object and represent them as a mock sequence:

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
  LOC_Gain = ∑ [ |Ej| × (|C(Ej)| - 1) ]
  ```
  This measures how many lines can be factored out across shared mock setups.

---

### Step 7: Multiple Runs to Avoid Local Optima

- Because tie-breaks are random, different runs may yield different results.
- Run the algorithm **multiple times** (e.g., 100 iterations), each with a different random seed.
- Select the run that achieves the **highest total LOC gain** — this approximates the global optimum and avoids being stuck in suboptimal assignment paths.

---

## 5. Detailed Example (Partial Overlaps)

Below is a contrived but concrete scenario to illustrate partial overlaps:

### 5.1. Mock Sequences

We have four mock sequences, each with some “creation” line plus different stubs:

1. `M1 = { mC, sA, sB }`

   ```java
   MyService s = Mockito.mock(MyService.class);  // mC
   when(s.doA("A")).thenReturn("someReturn");    // sA
   when(s.doB("B")).thenReturn("bReturn");       // sB
   ```

2. `M2 = { mC, sA, sC }`

   ```java
   MyService s = Mockito.mock(MyService.class);  // mC
   when(s.doA("A")).thenReturn("someReturn");    // sA
   when(s.doC("C")).thenReturn("cReturn");       // sC
   ```

3. `M3 = { mC, sD }`

   ```java
   MyService s = Mockito.mock(MyService.class);  // mC
   when(s.doD("D")).thenReturn("dReturn");       // sD
   ```

4. `M4 = { mC, sB, sC }`

   ```java
   MyService s = Mockito.mock(MyService.class);  // mC
   when(s.doB("B")).thenReturn("bReturn");       // sB
   when(s.doC("C")).thenReturn("cReturn");       // sC
   ```

### 5.2. Frequent Sub-sequences

Imagine Apriori/FP-Growth yields:

- `E1 = { mC }`  
  Covers `{M1, M2, M3, M4}`.  
- `E2 = { mC, sA }`  
  Covers `{M1, M2}`.  
- `E3 = { mC, sB }`  
  Covers `{M1, M4}`.  
- `E4 = { mC, sC }`  
  Covers `{M2, M4}`.  
- `E5 = { mC, sD }`  
  Covers `{M3}` only (so `|C(E5)|=1` for now).

### 5.3. Step-by-Step Possible Run

We sort them by `|Ej| * (|C(Ej)| - 1)`:

- `E1`: length = 1, covers 4 → potential = `1 * (4 - 1) = 3`.  
- `E2`: length = 2, covers 2 → potential = `2 * (2 - 1) = 2`.  
- `E3`: length = 2, covers 2 → potential = `2`.  
- `E4`: length = 2, covers 2 → potential = `2`.  
- `E5`: length = 2, covers 1 → potential = `0`.

1. **Assign M1**  
   - Candidates: `E1, E2, E3`.  
   - `E1=3`, `E2=2`, `E3=2`. Pick `E1`; now `E1` covers `{M1}`.  
2. **Assign M2**  
   - Candidates: `E1, E2, E4`. If `E1` is still 3, we pick `E1`; now `E1` covers `{M1, M2}`.  
3. **Assign M3**  
   - Candidates: `E1, E5`. `E1=3`, `E5=0`. Choose `E1`; now `E1` covers `{M1, M2, M3}`.  
4. **Assign M4**  
   - Candidates: `E1, E3, E4`. Possibly choose `E1` again; it covers `{M1, M2, M3, M4}`.

So `E1` length=1 covers 4 sequences → actual lines saved = `1 * (4 - 1) = 3`. Others might cover none or remain with coverage=1. Total = 3.

### 5.4. Another Run (With Different Choices)

If we **randomly** pick `E2` for `M2` instead, or if we try to use `E3` and `E4` for `M4`, we might produce a higher total. This is why multiple runs can be beneficial.

---

## 6. Conclusion

By focusing on **partial overlaps**:

1. We gather **mock sequences** from each test, even if they differ in some stubs.  
2. **Apriori/FP-Growth** detects sub-sequences that appear in multiple tests (like “mock creation + stub(A)”).  
3. We run a **greedy assignment** to see how best to group each mock sequence under a repeated sub-sequence, while acknowledging that leftover stubs can be done individually.  
4. We handle tie situations **randomly**, allowing multiple runs to find better coverage.  
5. We refactor out the most common overlapping lines into a helper method, thus **reducing duplication** while letting each test add the unique stubs it needs.

This approach systematically identifies repeated fragments—whether fully identical or partially shared—and helps keep tests more maintainable and concise.