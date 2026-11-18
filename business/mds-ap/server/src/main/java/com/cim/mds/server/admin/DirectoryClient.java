package com.cim.mds.server.admin;

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
