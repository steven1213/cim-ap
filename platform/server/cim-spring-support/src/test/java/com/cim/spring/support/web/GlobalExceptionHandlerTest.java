package com.cim.spring.support.web;

import com.cim.spring.support.i18n.MessageResolver;
import com.cim.spring.support.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import jakarta.validation.ConstraintViolationException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * T4.1 验收：字段级校验错误 + 5xx 不泄露内部细节。
 * 直接驱动 {@link GlobalExceptionHandler}，不依赖 web 容器。
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageResolver messageResolver;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(messageResolver);
        // MessageResolver 缺席时降级为默认文案（第三参兜底）
        when(messageResolver.resolve(any(), any(), any())).thenAnswer(i -> i.getArgument(2));
        TraceContext.put("trace-unit-test");
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("业务异常 → 业务码 + i18n 文案 + 正确 HTTP 状态")
    void handleBiz_mapsCodeAndStatus() {
        BizException ex = new BizException(BizCode.DATA_NOT_FOUND);
        ResponseEntity<Result<Void>> r = handler.handleBiz(ex);

        assertEquals(2001, r.getBody().code());
        assertEquals(404, r.getStatusCode().value());
        assertEquals("数据不存在", r.getBody().msg());
        assertNull(r.getBody().errors());
        assertEquals("trace-unit-test", r.getBody().traceId());
    }

    @Test
    @DisplayName("ValidationException → 400 + 字段级 errors")
    void handleValidation_hasFieldErrors() {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put("email", "邮箱格式不合法");
        fieldErrors.put("phone", "手机号不能为空");
        ResponseEntity<Result<Void>> r = handler.handleValidation(new ValidationException(fieldErrors));

        assertEquals(2000, r.getBody().code());
        assertEquals(400, r.getStatusCode().value());
        assertNotNull(r.getBody().errors());
        assertEquals(2, r.getBody().errors().size());
        assertEquals("邮箱格式不合法", r.getBody().errors().get("email"));
        assertEquals("手机号不能为空", r.getBody().errors().get("phone"));
    }

    @Test
    @DisplayName("@Valid 绑定校验失败 → 400 + 行内字段错误")
    void handleBind_populatesFieldErrors() {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "req");
        br.addError(new FieldError("req", "username", "用户名不能为空"));
        br.addError(new FieldError("req", "password", "密码长度不足"));
        ResponseEntity<Result<Void>> r = handler.handleBind(new BindException(br));

        assertEquals(400, r.getStatusCode().value());
        Map<String, String> errors = r.getBody().errors();
        assertNotNull(errors);
        assertTrue(errors.containsKey("username"));
        assertTrue(errors.containsKey("password"));
    }

    @Test
    @DisplayName("约束违反 → 400 + 字段级 errors（空集合也不 NPE）")
    void handleConstraint_noNpeOnEmpty() {
        ResponseEntity<Result<Void>> r = handler.handleConstraint(new ConstraintViolationException(java.util.Set.of()));

        assertEquals(400, r.getStatusCode().value());
        assertNotNull(r.getBody().errors());
        assertTrue(r.getBody().errors().isEmpty());
    }

    @Test
    @DisplayName("未预期异常 → 500 + 通用语 + traceId，且不泄露内部细节")
    void handleUnknown_doesNotLeakInternals() {
        RuntimeException root = new RuntimeException("SECURE_DETAIL_db_password_leak_xyz");
        ResponseEntity<Result<Void>> r = handler.handleUnknown(root);

        assertEquals(500, r.getStatusCode().value());
        assertEquals(9000, r.getBody().code());
        // 通用语，绝不含内部异常文本
        String msg = r.getBody().msg();
        assertEquals("系统繁忙，请稍后重试", msg);
        assertFalse(msg.contains("SECURE_DETAIL"), "5xx 响应不得泄露内部异常细节");
        assertNull(r.getBody().errors());
        assertEquals("trace-unit-test", r.getBody().traceId());
    }
}
