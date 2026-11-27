package com.cim.mds.server.admin;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.Result;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IAM 目录 API 只读客户端（identity-directory.md 方案 A 的服务间取数通道）。
 *
 * <p>统一抽出：六页共享组件的人员/组织数据面（{@code MdsDirectoryProxyController}）
 * 与业务页面的选人下拉（排班页等）共用同一通道——REST {@code GET /api/v1/directory/**}，
 * 服务身份 {@code X-Directory-Key}，只读、零缓存、不落库。谁可访问目录数据由调用方
 * 端点的 {@code @PreAuthorize} 决定，本客户端不做鉴权。</p>
 */
@Component
public class DirectoryClient {

    private final RestTemplate restTemplate;

    /** IAM 基地址（本地默认 8081；生产按部署注入）。 */
    private final String iamBaseUrl;

    /** 目录 API 服务身份密钥（与 IAM {@code cim.iam.directory.api.key} 同值，可轮换）。 */
    private final String directoryKey;

    public DirectoryClient(
            @Value("${cim.iam.base-url:http://localhost:8081}") String iamBaseUrl,
            @Value("${cim.iam.directory-key:dev-directory-key-change-me}") String directoryKey) {
        this.iamBaseUrl = StringUtils.trimTrailingCharacter(iamBaseUrl, '/');
        this.directoryKey = directoryKey;
        this.restTemplate = new RestTemplate();
    }

    /** 通用列表取数（Result 信封拆包；空结果统一为空列表）。 */
    public List<Map<String, Object>> list(String path, Map<String, String> query) {
        return listTyped(path, query, new ParameterizedTypeReference<Result<List<Map<String, Object>>>>() {
        });
    }

    /**
     * 组织节点选项（全维度全量启用节点，带 {@code parentCode} 父子关系——前端建组织树）。
     * 下拉展示整棵组织树作层级上下文，仅 {@code nodeType=DEPT} 且启用的节点可选。
     */
    public List<OrgNodeOption> orgOptions() {
        List<Map<String, Object>> rows = list("/api/v1/directory/orgs", Map.of());
        Map<String, String> idToCode = new HashMap<>();
        for (Map<String, Object> o : rows) {
            idToCode.put(String.valueOf(o.get("id")), String.valueOf(o.get("code")));
        }
        return rows.stream()
                .map(o -> new OrgNodeOption(
                        String.valueOf(o.get("code")),
                        String.valueOf(o.get("name")),
                        resolveParentCode(o, idToCode),
                        String.valueOf(o.getOrDefault("nodeType", "")),
                        isEnabled(o.get("status")),
                        nullIfNone(o.get("leaderName"))))
                .toList();
    }

    /** 目录 JSON 的 "null" 字符串哨兵 → Java null（GET /directory/orgs 的 map 通道）。 */
    private static String nullIfNone(Object v) {
        return v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v);
    }

    /** parentId（id 引用）→ 父节点 code；根节点或父缺失（目录被删）为 null，前端按根挂载。 */
    private static String resolveParentCode(Map<String, Object> o, Map<String, String> idToCode) {
        Object parentId = o.get("parentId");
        if (parentId == null || "null".equals(String.valueOf(parentId))) {
            return null;
        }
        return idToCode.get(String.valueOf(parentId));
    }

    /** 目录节点状态 → 启用与否（status 缺省视为启用，与 IAM 控制台口径一致）。 */
    private static boolean isEnabled(Object status) {
        return status == null || "ENABLED".equals(String.valueOf(status));
    }

    /**
     * 组织节点选项行（code 为引用真值；parentCode 供前端建树；
     * leaderName 为负责人显示名——Hold 到部门的任务路由目标）。
     */
    public record OrgNodeOption(
            String code, String name, String parentCode, String nodeType,
            boolean enabled, String leaderName) {
    }

    /**
     * 部门编码引用守门（V36 起部门真源 = IAM 组织架构，不再用静态字典）。
     * 空入空出；非空必须是启用 DEPT 节点编码，否则 PARAM_INVALID；
     * 目录服务不可达 → OPERATION_NOT_ALLOWED（明确报错而非静默放过）。
     */
    public String requireDeptCode(String value) {
        String code = value == null || value.isBlank() ? null : value.trim();
        if (code == null) {
            return null;
        }
        List<OrgNodeOption> orgs;
        try {
            orgs = orgOptions();
        } catch (Exception e) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.dept.directoryDown", e.getMessage());
        }
        boolean valid = orgs.stream().anyMatch(o ->
                code.equals(o.code()) && "DEPT".equals(o.nodeType()) && o.enabled());
        if (!valid) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dept.notFound", code);
        }
        return code;
    }

    /** 元素为标量（如用户 ID 字符串数组）的列表取数。 */
    public List<String> listStrings(String path, Map<String, String> query) {
        return listTyped(path, query, new ParameterizedTypeReference<Result<List<String>>>() {
        });
    }

    /** 泛型取数内核。 */
    private <T> List<T> listTyped(String path, Map<String, String> query,
            ParameterizedTypeReference<Result<List<T>>> type) {
        StringBuilder url = new StringBuilder(iamBaseUrl + path);
        if (!query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : query.entrySet()) {
                if (!first) url.append('&');
                url.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
        }
        ResponseEntity<Result<List<T>>> resp = restTemplate.exchange(
                url.toString(),
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                type);
        List<T> data = resp.getBody() == null ? null : resp.getBody().data();
        return data == null ? List.of() : data;
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Directory-Key", directoryKey);
        h.set("Accept", "application/json");
        return h;
    }
}
