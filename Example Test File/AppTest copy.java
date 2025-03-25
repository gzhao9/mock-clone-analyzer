package com.example;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.util.*;
import static org.mockito.Mockito.*;

class ComprehensiveMockTest {
    @Mock private DatabaseService dbMock; // 纯粹字段初始化
    private PaymentService paymentService; // 延迟初始化
    
    @Mock private Logger loggerMock; // 注解字段初始化
    private List<String> cachedList;

    // ----------------- Reusable Helper Methods -----------------
    private Order createMockOrder(String status) {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(status);
        return order;
    }

    // ----------------- Setup Methods -----------------
    @BeforeEach
    void setupGlobalMocks() {
        paymentService = Mockito.mock(PaymentService.class);
        when(paymentService.getCurrency()).thenReturn("USD"); // @Before 中的 Stub

        MockitoAnnotations.openMocks(this); // 激活 @Mock 注解
    }

    @AfterEach
    void cleanup() {
        verifyNoMoreInteractions(loggerMock); // 所有测试完成后验证
    }

    // ----------------- Test Cases -----------------
    @Test
    void testSingleTestLocalMock() {
        // TEST CASE 场景创建 + Stubbing
        UserService userService = mock(UserService.class);
        when(userService.findUser(anyString())).thenReturn(new User("test-user"));
        
        // Helper 方法中的 Stub
        setupUserPermissions(userService);
        
        // Verification
        verify(userService, atLeastOnce()).findUser(isNull());
    }

    @Test
    void testGlobalMockReuse() {
        dbMock = Mockito.mock(DatabaseService.class); // 覆盖 @Mock 字段
        when(dbMock.executeQuery("SELECT *")).thenReturn(100);
        
        // 不同位置的多次调用
        executeMockOperations(dbMock);
        executeMockOperations(dbMock);
    }

    @Test
    void testExceptionHandling() {
        // BDD 风格链式调用
        given(paymentService.processPayment(anyDouble()))
            .willThrow(new PaymentException())
            .willReturn(true);

        // Stub 后的调用验证
        Assertions.assertThrows(PaymentException.class, () -> {
            paymentService.processPayment(100.0);
        });
    }

    // ----------------- Complex Cases -----------------
    @Test
    void testMultipleAssignments() {
        cachedList = mock(ArrayList.class); // ASSIGNMENT
        cachedList.add("first");
        
        // 通过 Helper 方法在多种位置操作
        modifyCachedList(cachedList, 3);
        modifyCachedList(cachedList, 5);

        // Verification 多次验证
        verify(cachedList, times(2)).clear();
    }

    @Test
    void testMixedAnnotationUsage() {
        // @Spy 注解创建
        @Spy List<String> spyList = new ArrayList<>();
        doReturn(false).when(spyList).isEmpty(); // doReturn 语法
        
        // Helper 方法操作
        addTestData(spyList);
        verify(spyList).add("data");
    }

    @ParameterizedTest
    @EnumSource(
            value = HttpMethod.class,
            names = {"PUT", "POST", "PATCH"})
    void postRequestsShouldHaveEmptyBody(HttpMethod method)
            throws Exception { // Unexpected exception thrown: java.lang.IllegalArgumentException:
        // method POST must have a request body.
        final AuthenticationProvider authenticationProviderMock =
                mock(AuthenticationProvider.class);
        final var adapter =
                new OkHttpRequestAdapter(authenticationProviderMock) {
                    public Request test() throws Exception {
                        RequestInformation ri = new RequestInformation();
                        ri.httpMethod = method;
                        ri.urlTemplate = "http://localhost:1234";
                        Span span1 = GlobalOpenTelemetry.getTracer("").spanBuilder("").startSpan();
                        Span span2 = GlobalOpenTelemetry.getTracer("").spanBuilder("").startSpan();
                        return this.getRequestFromRequestInformation(ri, span1, span2);
                    }
                };

        final var request = assertDoesNotThrow(() -> adapter.test());
        assertNotNull(request.body());
    }

    // ----------------- Helper Methods -----------------
    private void setupUserPermissions(UserService userService) {
        // Helper 中的 Stubbing
        when(userService.hasPermission(any())).thenReturn(true);
    }

    private void executeMockOperations(DatabaseService db) {
        // Helper 中的 Verification
        db.connect();
        verify(db).connect();
    }

    private void modifyCachedList(List<String> list, int times) {
        // Helper 中多次调用
        for (int i=0; i<times; i++) {
            list.clear();
            list.add("modified-" + i);
        }
    }

    private void addTestData(List<String> list) {
        list.add("data");
    }
}
