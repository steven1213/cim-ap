package com.cim.obs.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataMaskerTest {

    @Test
    @DisplayName("手机号保留前3后4，中间掩码")
    void masksPhone() {
        String out = SensitiveDataMasker.mask("用户 13800138000 登录");
        assertEquals("用户 138****8000 登录", out);
    }

    @Test
    @DisplayName("邮箱仅保留首字符 + 域名")
    void masksEmail() {
        String out = SensitiveDataMasker.mask("联系 alice.wang@corp.com 失败");
        assertEquals("联系 a*********@corp.com 失败", out);
    }

    @Test
    @DisplayName("身份证保留前6后4")
    void masksIdCard() {
        String out = SensitiveDataMasker.mask("id=11010519491231002X");
        assertEquals("id=110105********002X", out);
    }

    @Test
    @DisplayName("银行卡仅保留后4位")
    void masksBankCard() {
        String out = SensitiveDataMasker.mask("card 6222021234567890123 扣款");
        assertEquals("card ***************0123 扣款", out);
    }

    @Test
    @DisplayName("key=value 形式口令/令牌值被掩码")
    void masksSecretKeyValue() {
        String out = SensitiveDataMasker.mask("password=Abc@12345&token=eyJhbGci.token.body");
        assertTrue(out.contains("password=***"), out);
        assertTrue(out.contains("token=***"), out);
        assertFalse(out.contains("Abc@12345"), "明文口令不得残留");
        assertFalse(out.contains("eyJhbGci"), "明文令牌不得残留");
    }

    @Test
    @DisplayName("混合 PII 一次全脱敏且不破坏非敏感文本")
    void masksMixed() {
        String in = "user=13800138000 email=foo@bar.com pwd=secret123 card=6222021234567890";
        String out = SensitiveDataMasker.mask(in);
        assertTrue(out.contains("138****8000"));
        assertTrue(out.contains("f**@bar.com"));
        assertTrue(out.contains("pwd=***"));
        assertTrue(out.contains("7890"));
        assertFalse(out.contains("6222021234567890"), "明文银行卡不得残留");
    }

    @Test
    @DisplayName("null 安全返回 null")
    void nullSafe() {
        assertNull(SensitiveDataMasker.mask(null));
    }
}
