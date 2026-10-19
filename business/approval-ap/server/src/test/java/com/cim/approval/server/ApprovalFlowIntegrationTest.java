package com.cim.approval.server;

import com.cim.approval.server.common.HashChainUtil;
import com.cim.approval.server.domain.ApprovalAudit;
import com.cim.approval.server.domain.CallbackOutbox;
import com.cim.approval.server.domain.CallbackRegistration;
import com.cim.approval.server.dto.AddSignRequest;
import com.cim.approval.server.dto.CompleteTaskRequest;
import com.cim.approval.server.dto.ReassignRequest;
import com.cim.approval.server.dto.ReturnRequest;
import com.cim.approval.server.dto.DeployProcessRequest;
import com.cim.approval.server.dto.StartProcessRequest;
import com.cim.approval.server.dto.TaskView;
import com.cim.approval.server.repository.CallbackOutboxRepository;
import com.cim.approval.server.repository.CallbackRegistrationRepository;
import com.cim.approval.server.service.ApprovalAddSignService;
import com.cim.approval.server.service.ApprovalProcessService;
import com.cim.approval.server.service.ApprovalReassignService;
import com.cim.approval.server.service.ApprovalReturnService;
import com.cim.approval.server.service.AuditService;
import com.cim.approval.server.service.CallbackService;
import com.sun.net.httpserver.HttpServer;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.cim.spring.support.web.BizException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ApprovalFlowIntegrationTest {

    @Autowired ApprovalProcessService processService;
    @Autowired ApprovalReturnService returnService;
    @Autowired ApprovalAddSignService addSignService;
    @Autowired ApprovalReassignService reassignService;
    @Autowired CallbackService callbackService;
    @Autowired AuditService auditService;
    @Autowired CallbackOutboxRepository outboxRepository;
    @Autowired CallbackRegistrationRepository registrationRepository;
    @Autowired HistoryService historyService;

    static HttpServer server;
    static String BASE_URL;
    static final String SECRET = "unit-test-secret-key-0123456789";
    static volatile String mockSecret = SECRET;
    static volatile boolean failNext = false;
    static final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/cb", exchange -> {
            String ts = exchange.getRequestHeaders().getFirst("X-Approval-Timestamp");
            String sig = exchange.getRequestHeaders().getFirst("X-Approval-Signature");
            String msgId = exchange.getRequestHeaders().getFirst("X-Approval-Message-Id");
            byte[] body = exchange.getRequestBody().readAllBytes();
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            boolean hmacOk = CallbackService.verifySignature(mockSecret, ts, bodyStr, sig);
            received.add(msgId + ":" + hmacOk);
            int code = failNext ? 500 : 200;
            byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
        BASE_URL = "http://localhost:" + server.getAddress().getPort() + "/cb";
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static StartProcessRequest single(String bk, String tenant, String assignee) {
        return new StartProcessRequest("singleApproval", bk, tenant, "initiator", "单级审批",
                null, assignee, null, null, null, null, null);
    }

    private static StartProcessRequest countersign(String bk, String tenant, List<String> approvers) {
        return new StartProcessRequest("countersignApproval", bk, tenant, "initiator", "会签",
                approvers, null, null, null, null, null, null);
    }

    private static String taskIdFor(List<TaskView> todos, String user) {
        return todos.stream().filter(t -> user.equals(t.assignee())).map(TaskView::taskId).findFirst()
                .orElseThrow(() -> new AssertionError("未找到用户 " + user + " 的待办"));
    }

    @Test
    void singleApproval_approve_endsAndEnqueuesCallback() {
        registrationRepository.save(reg("appX", BASE_URL));
        String bk = "bk-single-1";
        processService.startProcess(single(bk, "appX", "alice"));
        String taskId = taskIdFor(processService.listTodo("alice", "appX"), "alice");
        processService.completeTask(taskId, new CompleteTaskRequest("APPROVE", "同意", "alice"));

        List<CallbackOutbox> outboxes = outboxRepository.findByBusinessKey(bk);
        assertEquals(1, outboxes.size());
        CallbackOutbox o = outboxes.get(0);
        callbackService.deliverMessage(o.getMessageId());

        assertEquals(CallbackOutbox.Status.SENT, outboxRepository.findByMessageId(o.getMessageId()).get().getStatus());
        assertTrue(received.stream().anyMatch(s -> s.startsWith(o.getMessageId() + ":true")));
        verifyAuditChain(bk);
    }

    @Test
    void countersign_majorityApprove_endsApprove() {
        String bk = "bk-cs-approve";
        processService.startProcess(countersign(bk, "default", List.of("a", "b", "c")));
        processService.completeTask(taskIdFor(processService.listTodo("a", "default"), "a"),
                new CompleteTaskRequest("APPROVE", "ok", "a"));
        processService.completeTask(taskIdFor(processService.listTodo("b", "default"), "b"),
                new CompleteTaskRequest("APPROVE", "ok", "b"));
        // 第 2 个赞成即达阈值 ceil(3/2)=2，流程结束
        assertTrue(processService.listTodo("c", "default").isEmpty());
        assertEquals("APPROVED", readResult(bk));
        verifyAuditChain(bk);
    }

    @Test
    void countersign_majorityReject_endsRejectEarly() {
        String bk = "bk-cs-reject";
        processService.startProcess(countersign(bk, "default", List.of("a", "b", "c")));
        processService.completeTask(taskIdFor(processService.listTodo("a", "default"), "a"),
                new CompleteTaskRequest("REJECT", "no", "a"));
        processService.completeTask(taskIdFor(processService.listTodo("b", "default"), "b"),
                new CompleteTaskRequest("REJECT", "no", "b"));
        // 反对数 2 > (3-2=1) → 提前驳回，c 不再有任务
        assertTrue(processService.listTodo("c", "default").isEmpty());
        assertEquals("REJECTED", readResult(bk));
    }

    @Test
    void returnTask_backToLeaderCreatesNewTask() {
        String bk = "bk-return";
        processService.startProcess(new StartProcessRequest("twoStepApproval", bk, "default", "init", "两级",
                null, null, Map.of("leader", "alice", "manager", "bob"), null, null, null, null));
        processService.completeTask(taskIdFor(processService.listTodo("alice", "default"), "alice"),
                new CompleteTaskRequest("APPROVE", "ok", "alice"));
        // bob 当前待办
        String bobTask = taskIdFor(processService.listTodo("bob", "default"), "bob");
        returnService.returnTask(bobTask, new ReturnRequest("leaderReview", "退回修改", "bob"));
        // 退回首节点 leaderReview 触发「重提模式」：任务改名「修改并重新提交」并改派发起人 init
        assertFalse(processService.listTodo("init", "default").isEmpty());
        boolean isResubmit = processService.listTodo("init", "default").stream()
                .anyMatch(t -> ApprovalReturnService.RESUBMIT_TASK_NAME.equals(t.name()));
        assertTrue(isResubmit);
        boolean hasReturn = auditService.listByBusinessKey(bk).stream()
                .anyMatch(a -> "RETURN".equals(a.getAction()));
        assertTrue(hasReturn);
    }

    @Test
    void addSignBefore_movesAssigneeToNewSigner() {
        String bk = "bk-addsign";
        processService.startProcess(single(bk, "default", "alice"));
        String taskId = taskIdFor(processService.listTodo("alice", "default"), "alice");
        addSignService.addSign(taskId, new AddSignRequest("bob", "BEFORE", "加签", "alice"));
        // 前加签：bob 成为当前办理人
        String newAssignee = processService.listTodo("bob", "default").get(0).assignee();
        assertEquals("bob", newAssignee);
    }

    @Test
    void reassign_changesAssignee() {
        String bk = "bk-reassign";
        processService.startProcess(single(bk, "default", "alice"));
        String taskId = taskIdFor(processService.listTodo("alice", "default"), "alice");
        reassignService.reassign(taskId, new ReassignRequest("bob", "转交", "alice"));
        assertTrue(processService.listTodo("bob", "default").stream()
                .anyMatch(t -> t.taskId().equals(taskId)));
    }

    @Test
    void callback_retryThenSuccess_and_idempotent() {
        registrationRepository.save(reg("appY", BASE_URL));
        String bk = "bk-retry";
        processService.startProcess(single(bk, "appY", "alice"));
        String taskId = taskIdFor(processService.listTodo("alice", "appY"), "alice");
        processService.completeTask(taskId, new CompleteTaskRequest("APPROVE", "ok", "alice"));
        CallbackOutbox o = outboxRepository.findByBusinessKey(bk).get(0);

        failNext = true;
        callbackService.deliverMessage(o.getMessageId());
        assertEquals(CallbackOutbox.Status.PENDING, outboxRepository.findByMessageId(o.getMessageId()).get().getStatus());

        failNext = false;
        callbackService.deliverMessage(o.getMessageId());
        assertEquals(CallbackOutbox.Status.SENT, outboxRepository.findByMessageId(o.getMessageId()).get().getStatus());

        // 已发送后重复投递应幂等（不再改变状态、不再重复投递）
        int before = received.size();
        callbackService.deliverMessage(o.getMessageId());
        assertEquals(CallbackOutbox.Status.SENT, outboxRepository.findByMessageId(o.getMessageId()).get().getStatus());
        assertEquals(before, received.size());
    }

    // ---- helpers ----

    /** 角色选人（G6 / D8）测试用 BPMN：单个审批节点引用 ${ccg_review} 候选组变量。 */
    private static final String ROLE_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://cim/approval/test">
              <process id="roleG6Approval" name="角色选人审批" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="review" name="部门经理审批" flowable:candidateGroups="${ccg_review}"/>
                <endEvent id="end"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
                <sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Test
    void g6_roleCandidate_visibleOnlyToMatchingRole() {
        processService.deployProcess(
                new DeployProcessRequest("roleG6Approval", "角色选人审批", ROLE_BPMN, null));
        String bk = "g6-" + System.nanoTime();
        StartProcessRequest req = new StartProcessRequest("roleG6Approval", bk, "g6-domain", "g6init",
                "G6 角色选人", null, null, null, null, null, null,
                java.util.Map.of("review", java.util.List.of("manager")));
        String instId = processService.startProcess(req);

        // 角色命中：manager 成员可见该候选任务
        List<TaskView> mgr = processService.listTodo("alice", java.util.List.of("manager"), "g6-domain");
        assertTrue(mgr.stream().anyMatch(t -> t.processInstanceId().equals(instId)),
                "manager 角色成员应可见角色候选任务");

        // 角色未命中：engineer 不可见
        List<TaskView> eng = processService.listTodo("bob", java.util.List.of("engineer"), "g6-domain");
        assertFalse(eng.stream().anyMatch(t -> t.processInstanceId().equals(instId)),
                "engineer 角色成员不应可见");

        // 无角色 claim：不可见
        List<TaskView> none = processService.listTodo("carol", java.util.List.of(), "g6-domain");
        assertFalse(none.stream().anyMatch(t -> t.processInstanceId().equals(instId)),
                "无角色 claim 的用户不应可见");

        // 授权校验：manager 成员可办理（不抛异常）
        String taskId = mgr.stream().filter(t -> t.processInstanceId().equals(instId))
                .findFirst().orElseThrow().taskId();
        processService.assertTaskOperator(taskId, "alice", java.util.List.of("manager"));

        // 授权校验：engineer 非候选 → 拒绝
        assertThrows(BizException.class,
                () -> processService.assertTaskOperator(taskId, "bob", java.util.List.of("engineer")));

        // 办理 → 流程结束，待办清空
        processService.completeTask(taskId, new CompleteTaskRequest("APPROVE", "ok", "alice"));
        assertTrue(processService.listTodo("alice", java.util.List.of("manager"), "g6-domain").stream()
                .noneMatch(t -> t.processInstanceId().equals(instId)), "完成后待办应清空");
    }

    @Test
    void g6_roleCandidate_missingCandidateGroupsRejected() {
        processService.deployProcess(
                new DeployProcessRequest("roleG6Approval", "角色选人审批", ROLE_BPMN, null));
        // 定义含 ccg_review 引用但发起未提供 candidateGroups → 应 400 拒绝
        StartProcessRequest req = new StartProcessRequest("roleG6Approval", "g6-validate-" + System.nanoTime(),
                "g6-domain", "g6init", "G6 校验", null, null, null, null, null, null, null);
        assertThrows(BizException.class, () -> processService.startProcess(req));
    }

    private CallbackRegistration reg(String appCode, String baseUrl) {
        CallbackRegistration r = new CallbackRegistration();
        r.setId(java.util.UUID.randomUUID().toString());
        r.setAppCode(appCode);
        r.setBaseUrl(baseUrl);
        r.setSecret(SECRET);
        r.setEnabled(true);
        r.setCreatedAt(java.time.Instant.now());
        r.setUpdatedAt(java.time.Instant.now());
        return r;
    }

    private String readResult(String businessKey) {
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey).singleResult();
        if (hpi == null) {
            return null;
        }
        HistoricVariableInstance v = historyService
                .createHistoricVariableInstanceQuery()
                .processInstanceId(hpi.getId()).variableName("result").singleResult();
        return v == null ? null : String.valueOf(v.getValue());
    }

    private void verifyAuditChain(String businessKey) {
        List<ApprovalAudit> audits = auditService.listByBusinessKey(businessKey);
        assertFalse(audits.isEmpty());
        String prev = HashChainUtil.GENESIS;
        for (ApprovalAudit a : audits) {
            String expected = HashChainUtil.hashEvent(prev, a.getBusinessKey(), a.getAction(),
                    a.getActor(), a.getBeforeState(), a.getAfterState(), a.getDetail(),
                    a.getCreatedAt().toEpochMilli());
            assertEquals(expected, a.getHash(), "审计哈希链在第 " + a.getSeq() + " 条断裂");
            prev = a.getHash();
        }
    }
}
