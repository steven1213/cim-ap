package com.cim.mds.server.dynamicquery;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cim.mds.server.security.MdsPermissions;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.RawResponse;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动态视图运行时契约（V39）——MDS 自身与 MES 共用的渲染/取数端点。
 *
 * <p>① {@code GET /view-models/{code}/meta}：列元数据（前端动态构造 DataTable）；
 * ② {@code POST /view-models/{code}/data}：分页数据（参数强类型校验、页大小封顶）；
 * ③ {@code GET /view-models/{code}/export}：CSV 导出（allow_export 管控，1 万行封顶）。
 * 访问权限：viewmodel.perm_code 非空则要求该权限码，为空回落 mds:dynamic:view。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class DynamicViewController {

    private final ViewModelAdminService viewModelService;
    private final CimViewModelRepository viewModelRepository;
    private final CimQueryDefRepository queryDefRepository;
    private final CimQueryParamRepository queryParamRepository;
    private final DynamicQueryExecutor executor;

    // ------------------------------------------------------------------ meta

    public record MetaParam(String key, String label, String type, Boolean required, String dictCode) {
    }

    public record MetaCol(String key, String colTitle, String titleKey, Integer width,
                          String align, Boolean visible, String formatType, String formatArg) {
    }

    public record Meta(String viewCode, String viewName, String queryCode, Boolean allowExport,
                       Integer pageSize, List<MetaParam> params, List<MetaCol> columns) {
    }

    @GetMapping("/view-models/{code}/meta")
    public Result<Meta> meta(@PathVariable String code) {
        CimViewModel vm = requireByCode(code);
        requireAccess(vm);
        if (!Boolean.TRUE.equals(vm.getEnabled())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.dq.viewDisabled", code);
        }
        CimQueryParamRepository qpRepo = queryParamRepository;
        List<CimQueryParam> params = paramsOf(vm);
        List<MetaParam> paramMetas = params.stream()
                .map(p -> new MetaParam(p.getParamKey(), p.getParamLabel(), p.getParamType(),
                        p.getRequired(), p.getDictCode()))
                .toList();
        List<MetaCol> colMetas = viewModelService.get(vm.getId()).cols().stream()
                .map(c -> new MetaCol(c.colKey(), c.colTitle(), c.titleKey(), c.width(),
                        c.align(), c.visible(), c.formatType(), c.formatArg()))
                .toList();
        return Result.ok(new Meta(vm.getViewCode(), vm.getViewName(), vm.getQueryCode(),
                vm.getAllowExport(), vm.getPageSize(), paramMetas, colMetas));
    }

    // ------------------------------------------------------------------ data

    public record DataReq(Map<String, String> params, Integer page, Integer size) {
    }

    @PostMapping("/view-models/{code}/data")
    public Result<DynamicQueryExecutor.PageData> data(
            @PathVariable String code, @RequestBody DataReq req) {
        CimViewModel vm = requireByCode(code);
        requireAccess(vm);
        if (!Boolean.TRUE.equals(vm.getEnabled())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.dq.viewDisabled", code);
        }
        CimQueryDef def = requireQuery(vm);
        Map<String, String> params = req.params() == null ? Map.of() : req.params();
        int page = req.page() == null ? 1 : req.page();
        int size = req.size() == null || req.size() < 1 ? vm.getPageSize() : req.size();
        return Result.ok(executor.page(def, paramsOf(vm), params, page, size));
    }

    // ------------------------------------------------------------------ export

    @RawResponse(value = "CSV 文件下载（Excel 兼容 UTF-8 BOM）——Content-Disposition 附件，JSON 信封会破坏文件本身",
            reason = RawResponse.Reason.DOWNLOAD)
    @GetMapping("/view-models/{code}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String code, @RequestParam Map<String, String> allRequestParams) {
        CimViewModel vm = requireByCode(code);
        requireAccess(vm);
        if (!Boolean.TRUE.equals(vm.getAllowExport())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.dq.exportForbidden", code);
        }
        CimQueryDef def = requireQuery(vm);
        Map<String, String> params = new LinkedHashMap<>(allRequestParams);
        params.remove("ignored");
        List<Map<String, Object>> rows = executor.exportRows(def, paramsOf(vm), params);
        List<ViewModelAdminService.ColView> cols = viewModelService.get(vm.getId()).cols().stream()
                .filter(c -> Boolean.TRUE.equals(c.visible()))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csv(cols.get(i).colTitle() != null ? cols.get(i).colTitle()
                    : cols.get(i).titleKey() != null ? cols.get(i).titleKey() : cols.get(i).colKey()));
        }
        sb.append('\r');
        sb.append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Object v = row.get(cols.get(i).colKey().toLowerCase());
                sb.append(csv(v == null ? "" : String.valueOf(v)));
            }
            sb.append('\r');
            sb.append('\n');
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "attachment; filename=" + vm.getViewCode() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    // ------------------------------------------------------------------ 内部

    private List<CimQueryParam> paramsOf(CimViewModel vm) {
        return queryDefRepository.findByQueryCodeAndDeletedFalse(vm.getQueryCode())
                .map(def -> queryParamRepository.findByQueryIdAndDeletedFalseOrderBySortNoAsc(def.getId()))
                .orElse(List.of());
    }

    private CimQueryDef requireQuery(CimViewModel vm) {
        return queryDefRepository.findByQueryCodeAndDeletedFalse(vm.getQueryCode())
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.dq.queryNotFound",
                        vm.getQueryCode()));
    }

    private CimViewModel requireByCode(String code) {
        return viewModelRepository.findByViewCodeAndDeletedFalse(code)
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.dq.viewModelNotFound", code));
    }

    /** 视图访问权限：perm_code 非空则要求该权限码，为空回落 mds:dynamic:view。 */
    private void requireAccess(CimViewModel vm) {
        String required = vm.getPermCode() == null || vm.getPermCode().isEmpty()
                ? MdsPermissions.DYNAMIC_VIEW : vm.getPermCode();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // 认证关闭（测试态/匿名）时与 @PreAuthorize 同语义放行——生产由过滤器链先行挡 401
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return;
        }
        boolean hit = auth.getAuthorities().stream().anyMatch(a -> required.equals(a.getAuthority()));
        if (!hit) {
            throw new AccessDeniedException(required);
        }
    }

    private String csv(String v) {
        String s = v == null ? "" : v;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
