package com.cim.approval.sdk;

import com.cim.approval.sdk.dto.AddSignRequest;
import com.cim.approval.sdk.dto.AuditView;
import com.cim.approval.sdk.dto.CallbackRegistrationRequest;
import com.cim.approval.sdk.dto.CallbackRegistrationView;
import com.cim.approval.sdk.dto.CompleteTaskRequest;
import com.cim.approval.sdk.dto.OutboxView;
import com.cim.approval.sdk.dto.ProcessDefinitionView;
import com.cim.approval.sdk.dto.ProcessInstanceView;
import com.cim.approval.sdk.dto.ReassignRequest;
import com.cim.approval.sdk.dto.ReturnRequest;
import com.cim.approval.sdk.dto.StartProcessRequest;
import com.cim.approval.sdk.dto.TaskView;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批流平台（approval-ap）Java SDK 客户端。
 * 基于 JDK HttpClient + Jackson，无 Spring 强制依赖，可作为普通 jar 嵌入任意业务方（如 RMS）。
 *
 * <p>基址示例：http://localhost:8085（approval-ap 默认端口）。所有接口位于 /api/v1/approval 前缀下。</p>
 *
 * <p>典型用法：
 * <pre>{@code
 *   ApprovalClient client = new ApprovalClient("http://localhost:8085");
 *   String piId = client.startProcess(new StartProcessRequest(
 *       "countersignApproval", "bk-001", "rms-ap", "alice", "配方签核",
 *       List.of("bob", "carol"), null, null));
 *   for (TaskView t : client.listTodo("bob", "rms-ap")) {
 *       client.complete(t.taskId(), new CompleteTaskRequest("APPROVE", "同意", "bob"));
 *   }
 * }</pre>
 */
public class ApprovalClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private static final String PREFIX = "/api/v1/approval";

    public ApprovalClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private String url(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(baseUrl).append(PREFIX).append(path);
        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                sb.append(first ? "?" : "&");
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }
        return sb.toString();
    }

    private HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("approval-ap HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp;
    }

    private <E> List<E> getList(String path, Map<String, String> params, Class<E> elemType) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url(path, params))).GET();
        HttpResponse<String> resp = send(b);
        return mapper.readValue(resp.body(),
                mapper.getTypeFactory().constructCollectionType(List.class, elemType));
    }

    @SuppressWarnings("unchecked")
    private <T> T post(String path, Object body, Class<T> type) throws Exception {
        String json = mapper.writeValueAsString(body);
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url(path, null)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> resp = send(b);
        if (type == null) {
            return null;
        }
        return (T) mapper.readValue(resp.body(), type);
    }

    // ---------- 流程定义 / 发起 ----------
    public List<ProcessDefinitionView> listDefinitions() throws Exception {
        return getList("/definitions", null, ProcessDefinitionView.class);
    }

    public String startProcess(StartProcessRequest req) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url("/processes/start", null)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(req)));
        HttpResponse<String> resp = send(b);
        Map<String, String> m = mapper.readValue(resp.body(), Map.class);
        return m.get("processInstanceId");
    }

    // ---------- 任务 ----------
    public List<TaskView> listTodo(String userId, String tenantId) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("userId", userId);
        if (tenantId != null) {
            p.put("tenantId", tenantId);
        }
        return getList("/tasks/todo", p, TaskView.class);
    }

    public List<TaskView> listDone(String userId, String tenantId) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("userId", userId);
        if (tenantId != null) {
            p.put("tenantId", tenantId);
        }
        return getList("/tasks/done", p, TaskView.class);
    }

    public List<ProcessInstanceView> listInitiated(String userId, String tenantId) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("userId", userId);
        if (tenantId != null) {
            p.put("tenantId", tenantId);
        }
        return getList("/tasks/initiated", p, ProcessInstanceView.class);
    }

    public void complete(String taskId, CompleteTaskRequest req) throws Exception {
        post("/tasks/" + taskId + "/complete", req, null);
    }

    public void returnTask(String taskId, ReturnRequest req) throws Exception {
        post("/tasks/" + taskId + "/return", req, null);
    }

    public void addSign(String taskId, AddSignRequest req) throws Exception {
        post("/tasks/" + taskId + "/add-sign", req, null);
    }

    public void reassign(String taskId, ReassignRequest req) throws Exception {
        post("/tasks/" + taskId + "/reassign", req, null);
    }

    // ---------- 回调注册 / 查询 ----------
    public CallbackRegistrationView registerCallback(CallbackRegistrationRequest req) throws Exception {
        return post("/callbacks/register", req, CallbackRegistrationView.class);
    }

    public List<CallbackRegistrationView> listCallbacks() throws Exception {
        return getList("/callbacks", null, CallbackRegistrationView.class);
    }

    public List<OutboxView> queryOutbox(String appCode, String businessKey) throws Exception {
        Map<String, String> p = new HashMap<>();
        if (appCode != null) {
            p.put("appCode", appCode);
        }
        if (businessKey != null) {
            p.put("businessKey", businessKey);
        }
        return getList("/callbacks/outbox", p, OutboxView.class);
    }

    // ---------- 审计 ----------
    public List<AuditView> listAudit(String businessKey) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("businessKey", businessKey);
        return getList("/audit", p, AuditView.class);
    }
}
