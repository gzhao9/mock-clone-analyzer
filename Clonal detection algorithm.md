## 1. Mock Clone Overview (Partial Overlap Scenario)

When you use **Mockito** to write tests, you might have repeated lines that create a mock object and then set certain stubs:

```java
MyService s = Mockito.mock(MyService.class);
when(s.doA("A")).thenReturn("someReturn");
when(s.doB("B")).thenReturn("bReturn");
// Possibly more stubbing...
```

Sometimes, the stubs are **not all** the same. For instance, some tests also stub `doC("C")`, or `doD("D")`. But **part** of the stubbing is still identical—maybe you always stub `"A"`, but only half the tests also stub `"B"`. This means you can refactor out the **common lines** into a helper method, and then have each test do its **additional** stubs afterward:

```java
// A shared partial method:
private MyService getBaseMock() {
    MyService s = Mockito.mock(MyService.class);
    when(s.doA("A")).thenReturn("someReturn");  // common stub
    return s;
}

// In one test:
MyService s = getBaseMock();
when(s.doB("B")).thenReturn("bReturn");  // test-specific stubbing

// In another test:
MyService s = getBaseMock();
when(s.doC("C")).thenReturn("cReturn");  // different test-specific stubbing
```

Thus, you only **clone** the lines that are truly repeated in multiple tests. Our goal is to **automate** finding these partial overlaps (common subsequences). We do so by:

1. Collecting each test’s **mock sequence** (the lines that create and stub a particular mock).  
2. Using **Apriori** or **FP-Growth** to detect repeated sub-sequences.  
3. **Greedily assigning** each mock sequence to the “best” repeated sub-sequence, possibly adding leftover stubs by hand.

---

## 2. A Simple Look at Apriori/FP-Growth

- **Apriori** and **FP-Growth** are data-mining algorithms originally used for “shopping-basket” analysis. Each basket (or “transaction”) might contain items like {milk, bread, eggs}.  
- Here, **each mock sequence** plays the role of a “basket,” containing lines like `{mock creation, stub A, stub B}`.  
- Apriori systematically enumerates possible sub-sets (or sub-sequences) and keeps only those that appear in many baskets. FP-Growth uses a special tree structure to do this more efficiently.  
- The result is a set of **frequent sub-sequences** that appear in at least some threshold number of mock sequences. For instance, “mock creation + stub A” might appear in 10 tests, making it a common pattern.

---

## 3. Key Symbols and Meanings

1. \(\mathcal{M} = \{M_1, M_2, \dots, M_n\}\)  
   - Our **mock sequence** set. Each \(M_i\) is one test’s lines for a particular mock object. Example:
     \[
       M_1 = \{\texttt{mockCreation}, \texttt{stub(A)}, \texttt{stub(B)}\}.
     \]

2. \(\mathcal{E} = \{E_1, E_2, \dots, E_k\}\)  
   - The collection of **frequent stubbing sub-sequences** found by Apriori/FP-Growth.  
   - Example: \(E_2 = \{\texttt{mockCreation}, \texttt{stub(A)}\}\) if that pair is repeated across many tests.

3. \(C(E_j)\subseteq \mathcal{M}\)  
   - The set of mock sequences in which \(E_j\) appears (i.e., all tests that contain that sub-sequence).

4. **LOC Reduction (Gain) Formula**  
   \[
     \text{LOC Gain}
     = \sum_{j} \Bigl[\lvert E_j\rvert \times \bigl(\lvert C(E_j)\rvert -1\bigr)\Bigr].
   \]
   - \(\lvert E_j\rvert\): how many lines (stubs) are in sub-sequence \(E_j\).  
   - \(\lvert C(E_j)\rvert\): how many mock sequences it covers.  
   - If \(E_j\) only covers 1 mock sequence, it yields 0 net savings.

---

## 4. The Greedy Algorithm (Step by Step)

### A. Candidate Extraction
1. **Collect mock sequences** \(\{M_i\}\) from your tests.  
2. **Run Apriori or FP-Growth** to get frequent sub-sequences \(\{E_j\}\).  
3. Compute \(C(E_j)\), the set of mock sequences each \(E_j\) covers.

### B. Sort Sub-sequences
1. All sub-sequences \(\{E_j\}\) are **sorted** (often descending by `|E_j|*(|C(E_j)|-1)`) to prioritize those likely to save the most lines.

### C. Greedy Assignment (with Random Tie-Breaks)
- For each **unassigned** mock sequence \(M_i\):
  1. Find all \(E_j\) such that \(M_i \in C(E_j)\) (i.e., \(E_j\) is contained in \(M_i\)).  
  2. If none can cover \(M_i\), skip.  
  3. If exactly one can cover \(M_i\), assign \(M_i\) to that \(E_j\).  
  4. If multiple sub-sequences share the same priority, pick one randomly.  
  5. Mark \(M_i\) assigned.

### D. Dynamic Cleanup
1. If any sub-sequence \(E_j\) ends up covering only 1 mock sequence (\(\lvert C(E_j)\rvert=1\)), it yields no duplication savings; **remove** it.  
2. The sequence that was covered by it returns to “unassigned,” so it can be matched to something else.  
3. Repeat assignment until stable.

### E. Stopping and Final Calculation
- When no more changes occur, compute  
  \[
    \sum_{j} \Bigl[\lvert E_j\rvert \times (\lvert C(E_j)\rvert -1)\Bigr].
  \]
- That’s your **LOC saved** for this **one run**.

### F. Multiple Runs
- Because you **randomly break ties**, different runs can produce different coverage sets.  
- **Repeat** the entire process multiple times, picking the run that yields the highest final LOC savings.

---

## 5. Detailed Example (Partial Overlaps)

Below is a contrived but concrete scenario to illustrate partial overlaps:

### 5.1. Mock Sequences

We have four mock sequences, each with some “creation” line plus different stubs:

- \(M_1 = \{\texttt{mC},\; \texttt{sA},\; \texttt{sB}\}\)

  ```java
  MyService s = Mockito.mock(MyService.class);  // mC
  when(s.doA("A")).thenReturn("someReturn");    // sA
  when(s.doB("B")).thenReturn("bReturn");       // sB
  ```

- \(M_2 = \{\texttt{mC},\; \texttt{sA},\; \texttt{sC}\}\)

  ```java
  MyService s = Mockito.mock(MyService.class);  // mC
  when(s.doA("A")).thenReturn("someReturn");    // sA
  when(s.doC("C")).thenReturn("cReturn");       // sC
  ```

- \(M_3 = \{\texttt{mC},\; \texttt{sD}\}\)

  ```java
  MyService s = Mockito.mock(MyService.class);  // mC
  when(s.doD("D")).thenReturn("dReturn");       // sD
  ```

- \(M_4 = \{\texttt{mC},\; \texttt{sB},\; \texttt{sC}\}\)

  ```java
  MyService s = Mockito.mock(MyService.class);  // mC
  when(s.doB("B")).thenReturn("bReturn");       // sB
  when(s.doC("C")).thenReturn("cReturn");       // sC
  ```

### 5.2. Frequent Sub-sequences

Imagine Apriori/FP-Growth yields the following:

1. \(E_1=\{\texttt{mC}\}\).  
   - Appears in all four sequences (\(\{M_1, M_2, M_3, M_4\}\)).  
2. \(E_2=\{\texttt{mC}, \texttt{sA}\}\).  
   - Appears in \(M_1, M_2\).  
3. \(E_3=\{\texttt{mC}, \texttt{sB}\}\).  
   - Appears in \(M_1, M_4\).  
4. \(E_4=\{\texttt{mC}, \texttt{sC}\}\).  
   - Appears in \(M_2, M_4\).  
5. \(E_5=\{\texttt{mC}, \texttt{sD}\}\).  
   - Appears in \(M_3\) only (so \(|C(E_5)|=1\) for now).

(**Note**: These sub-sequences reflect the partial overlap—some share the creation line `mC` plus a different stub, some only appear in 2 sequences, and so on.)

### 5.3. Step-by-Step Possible Run

We sort sub-sequences by \(\lvert E_j\rvert \times (\lvert C(E_j)\rvert -1)\):

- \(E_1\): length = 1, covers 4 mock sequences => potential = \(1*(4-1)=3\).  
- \(E_2\): length = 2, covers 2 => potential = \(2*(2-1)=2\).  
- \(E_3\): length = 2, covers 2 => potential = \(2*(2-1)=2\).  
- \(E_4\): length = 2, covers 2 => potential = 2.  
- \(E_5\): length = 2, covers 1 => potential = 0.

1. **Assign \(M_1\)**  
   - Covers: \(E_1, E_2, E_3\).  
   - Highest potential so far might be \(E_1=3\) vs. \(E_2=2\) vs. \(E_3=2\). So we might pick **\(E_1\)** for \(M_1\). Now \(E_1\) covers \(\{M_1\}\).  
2. **Assign \(M_2\)**  
   - Covers: \(E_1, E_2, E_4\).  
   - If \(E_1\) still has the highest potential (3), we might pick it again. So now \(E_1\) covers \(\{M_1, M_2\}\).  
3. **Assign \(M_3\)**  
   - Covers: \(E_1, E_5\).  
   - \(E_1\) still 3 potential, \(E_5\) is effectively 0 if it only covers 1. So we pick \(E_1\). Now \(E_1\) covers \(\{M_1, M_2, M_3\}\).  
4. **Assign \(M_4\)**  
   - Covers: \(E_1, E_3, E_4\). Possibly we choose \(E_1\) again. Then \(E_1\) covers \(\{M_1, M_2, M_3, M_4\}\).  

**Final coverage**: \(E_1\) has length=1 and covers 4 sequences => actual lines saved = \(1*(4-1)=3\).  

- The other sub-sequences might cover none, or just 1.  
- That yields total of 3 lines saved.

### 5.4. Another Run (With Different Choices)

If, for instance, we **randomly** decided at \(M_2\) to pick \(E_2\) (the “mC + sA” pattern), then \(E_2\) would cover \(\{M_1, M_2\}\). Meanwhile, we could assign \(M_4\) to \(E_4\) = {mC, sC}, covering \(\{M_2, M_4\}\), etc. Potentially, these combos might produce a higher sum if they cover multiple sets with length≥2.  

Hence, we see the advantage of **random tie-breaking** and **multiple runs**—some final combos can cover more sequences with bigger sub-sequences, thus increasing the total lines saved. Meanwhile, any stubs not covered by that partial pattern can be added manually in each test.

---

## 6. Conclusion

By focusing on **partial overlaps**:

1. We gather **mock sequences** from each test, even if they differ in some stubs.  
2. **Apriori/FP-Growth** detects sub-sequences that appear in multiple tests (like “mock creation + stub(A)”).  
3. We run a **greedy assignment** to see how best to group each mock sequence under a repeated sub-sequence, while acknowledging that leftover stubs can be done individually.  
4. We handle tie situations **randomly**, allowing multiple runs to find better coverage.  
5. In the end, we refactor out the most common overlapping lines into a helper method, thus **reducing duplication** while letting each test add the unique stubs it needs.