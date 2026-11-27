package com.cim.mds.server.flow;

import com.cim.spring.support.web.Result;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * RMS IF-M2 HTTP 实现（服务间密钥 {@code X-Integration-Key}，镜像 DirectoryClient 口径）。
 *
 * <p>{@code cim.rms.base-url} 未配置时直接返回 RMS_UNAVAILABLE（不发起调用）——
 * dev 态允许只跑 FIXED 链路；网络失败也折算为 RMS_UNAVAILABLE（进站路径上
 * RMS 宕机 = 阻断并给出原因，不静默放行）。</p>
 */
@Slf4j
@Component
public class HttpRmsRecipeClient implements RmsRecipeClient {

    private final RestTemplate restTemplate = new RestTemplate();

    private final String baseUrl;

    private final String apiKey;

    public HttpRmsRecipeClient(
            @Value("${cim.rms.base-url:}") String baseUrl,
            @Value("${cim.rms.integration-key:dev-integration-key-change-me}") String apiKey) {
        this.baseUrl = baseUrl == null ? "" : StringUtils.trimTrailingCharacter(baseUrl.trim(), '/');
        this.apiKey = apiKey;
    }

    @Override
    public Lookup lookup(String deviceCode, String recipeCode) {
        if (baseUrl.isEmpty()) {
            return unavailable("未配置 cim.rms.base-url");
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/v1/rms/integration/recipe-ppid")
                    .queryParam("deviceCode", deviceCode)
                    .queryParam("recipeCode", recipeCode)
                    .build(true).toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Integration-Key", apiKey);
            headers.set("Accept", "application/json");
            ResponseEntity<Result<Lookup>> resp = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<Result<Lookup>>() {
                    });
            Result<Lookup> body = resp.getBody();
            Lookup data = body == null ? null : body.data();
            return data != null ? data : unavailable("RMS 返回空数据");
        } catch (Exception e) {
            log.warn("[recipe-resolve] RMS IF-M2 调用失败: {} {}", e.getClass().getSimpleName(), e.getMessage());
            return unavailable(e.getMessage());
        }
    }

    private static Lookup unavailable(String detail) {
        return new Lookup("RMS_UNAVAILABLE", null, null, null, null, null, detail);
    }
}
