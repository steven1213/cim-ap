package com.cim.spring.support.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 统一响应契约校验器：只允许 Result 与显式例外。 */
class ApiContractVerifierTest {

    @RestController
    static class GoodController {
        @GetMapping("/good")
        public Result<String> good() {
            return Result.ok("ok");
        }
    }

    @RestController
    static class BadController {
        @GetMapping("/bad")
        public Map<String, Object> bad() {
            return Map.of();
        }
    }

    @RestController
    static class AnnotatedController {
        @RawResponse(reason = RawResponse.Reason.DOWNLOAD, value = "导出文件")
        @GetMapping("/file")
        public byte[] file() {
            return new byte[0];
        }
    }

    @RestController
    @RawResponse(reason = RawResponse.Reason.STREAM)
    static class WholeClassExemptController {
        @GetMapping("/sse")
        public Object sse() {
            return new Object();
        }
    }

    @RestController
    static class AdviceOnlyController {
        @GetMapping("/good")
        public Result<String> good() {
            return Result.ok("ok");
        }

        @org.springframework.web.bind.annotation.ExceptionHandler(RuntimeException.class)
        public Object onError() {
            return new Object();
        }
    }

    private static DefaultListableBeanFactory factory(Object... controllers) {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        for (int i = 0; i < controllers.length; i++) {
            bf.registerSingleton("c" + i, controllers[i]);
        }
        return bf;
    }

    private static ApiResponseProperties props(boolean strict) {
        ApiResponseProperties p = new ApiResponseProperties();
        p.setStrict(strict);
        return p;
    }

    @Test
    void 全部返回_Result_时通过() {
        ApiContractVerifier verifier = new ApiContractVerifier(factory(new GoodController()), props(true));
        assertDoesNotThrow(verifier::afterSingletonsInstantiated);
    }

    @Test
    void 返回裸类型且未标注时严格模式启动失败() {
        ApiContractVerifier verifier = new ApiContractVerifier(factory(new BadController()), props(true));
        IllegalStateException e = assertThrows(IllegalStateException.class, verifier::afterSingletonsInstantiated);
        assertTrue(e.getMessage().contains("BadController#bad()"), e.getMessage());
        assertTrue(e.getMessage().contains("Map"), e.getMessage());
    }

    @Test
    void 返回裸类型但非严格模式仅告警() {
        ApiContractVerifier verifier = new ApiContractVerifier(factory(new BadController()), props(false));
        assertDoesNotThrow(verifier::afterSingletonsInstantiated);
    }

    @Test
    void 标注_RawResponse_的方法与类均被放行() {
        ApiContractVerifier verifier = new ApiContractVerifier(
                factory(new AnnotatedController(), new WholeClassExemptController()), props(true));
        assertDoesNotThrow(verifier::afterSingletonsInstantiated);
    }

    @Test
    void 异常处理方法不参与契约校验() {
        ApiContractVerifier verifier = new ApiContractVerifier(factory(new AdviceOnlyController()), props(true));
        assertDoesNotThrow(verifier::afterSingletonsInstantiated);
    }

    @Test
    void verify_关闭时整体跳过() {
        ApiResponseProperties p = props(true);
        p.setVerify(false);
        ApiContractVerifier verifier = new ApiContractVerifier(factory(new BadController()), p);
        assertDoesNotThrow(verifier::afterSingletonsInstantiated);
    }
}
