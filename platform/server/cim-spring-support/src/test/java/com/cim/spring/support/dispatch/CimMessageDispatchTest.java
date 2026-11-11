package com.cim.spring.support.dispatch;

import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 消息注册表与分发器：重复路径、泛型错绑、未知路径、坏报文。 */
class CimMessageDispatchTest {

    record LeaveApplyRequest(String userId, int days) {
    }

    record OvertimeRequest(String userId, int hours) {
    }

    static class LeaveProcessor implements CimMessageProcessor<LeaveApplyRequest> {
        @Override
        public Object process(LeaveApplyRequest request) {
            return "leave:" + request.userId() + ":" + request.days();
        }
    }

    private static CimDispatchProperties props() {
        CimDispatchProperties p = new CimDispatchProperties();
        p.setEnabled(true);
        return p;
    }

    private static CimMessageRegistry registry() {
        return new CimMessageRegistry(new DefaultListableBeanFactory(), props());
    }

    @Test
    void 默认路径由类名派生并归一化为小写() {
        assertEquals("/leaveApply", CimMessageRegistry.defaultPath(LeaveApplyRequest.class));
        assertEquals("/leaveapply", CimMessageRegistry.normalize(CimMessageRegistry.defaultPath(LeaveApplyRequest.class)));
        assertEquals("/a/b", CimMessageRegistry.normalize("a/b"));
    }

    @Test
    void 重复路径注册失败并给出双方类名() {
        CimMessageRegistry r = registry();
        r.register(new CimMessageRegistry.Entry("/x", LeaveApplyRequest.class, LeaveProcessor.class, "POST"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> r.register(new CimMessageRegistry.Entry("/x", OvertimeRequest.class, LeaveProcessor.class, "POST")));
        assertTrue(e.getMessage().contains("重复注册"), e.getMessage());
        assertTrue(e.getMessage().contains(OvertimeRequest.class.getName()), e.getMessage());
        assertTrue(e.getMessage().contains(LeaveApplyRequest.class.getName()), e.getMessage());
    }

    @Test
    void 处理器泛型与请求类型不匹配时注册失败() {
        CimMessageRegistry r = registry();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> r.register(new CimMessageRegistry.Entry("/overtime", OvertimeRequest.class,
                        LeaveProcessor.class, "POST")));
        assertTrue(e.getMessage().contains("泛型参数与请求类型不匹配"), e.getMessage());
    }

    @Test
    void 分发成功时由框架包信封() {
        CimMessageRegistry r = registry();
        r.register(new CimMessageRegistry.Entry("/leaveapply", LeaveApplyRequest.class, LeaveProcessor.class, "POST"));
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        bf.registerSingleton("leaveProcessor", new LeaveProcessor());
        CimMessageDispatcher d = new CimMessageDispatcher(r, bf, new ObjectMapper().findAndRegisterModules());

        Result<?> result = d.dispatch("/LeaveApply", "{\"userId\":\"u1\",\"days\":3}");
        assertEquals(0, result.code());
        assertEquals("leave:u1:3", result.data());
    }

    @Test
    void 未知路径报参数非法且不回显已注册清单() {
        CimMessageDispatcher d = new CimMessageDispatcher(registry(),
                new DefaultListableBeanFactory(), new ObjectMapper().findAndRegisterModules());
        BizException e = assertThrows(BizException.class, () -> d.dispatch("/nope", "{}"));
        assertNotNull(e.getBizCode());
        assertEquals(2000, e.getBizCode().getCode());
    }

    @Test
    void 报文无法反序列化时报参数非法并指明目标类型() {
        CimMessageRegistry r = registry();
        r.register(new CimMessageRegistry.Entry("/leaveapply", LeaveApplyRequest.class, LeaveProcessor.class, "POST"));
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        bf.registerSingleton("leaveProcessor", new LeaveProcessor());
        CimMessageDispatcher d = new CimMessageDispatcher(r, bf, new ObjectMapper().findAndRegisterModules());

        BizException e = assertThrows(BizException.class, () -> d.dispatch("/leaveapply", "{not-json"));
        assertTrue(e.getMessage().contains("LeaveApplyRequest"), e.getMessage());
    }

    @Test
    void 空报文按空对象处理() {
        CimMessageRegistry r = registry();
        r.register(new CimMessageRegistry.Entry("/leaveapply", LeaveApplyRequest.class, LeaveProcessor.class, "POST"));
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        bf.registerSingleton("leaveProcessor", new LeaveProcessor());
        CimMessageDispatcher d = new CimMessageDispatcher(r, bf, new ObjectMapper().findAndRegisterModules());

        Result<?> result = d.dispatch("/leaveapply", null);
        assertEquals("leave:null:0", result.data());
    }
}
