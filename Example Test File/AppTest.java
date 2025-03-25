package com.example;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.util.*;
import static org.mockito.Mockito.*;

// 静态导入 Mockito.mock
import static org.mockito.Mockito.mock;

class MyTest {
    @Mock UserService userService;  // 注解方式
  
    @Test
    void test() {
        // 旧方案会漏检（静态导入+注解）
        User user = mock(User.class); 
        when(user.getName()).thenReturn("Alice"); 
    }
}
