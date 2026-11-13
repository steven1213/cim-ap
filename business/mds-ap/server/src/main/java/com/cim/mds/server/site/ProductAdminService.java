package com.cim.mds.server.site;

import java.util.List;
import java.util.Set;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.system.dict.SysDictService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产品（Product）管理——产品管理域主数据。
 *
 * <p>权限 {@code mds:product:*}（控制器层把关）。行业通用口径：
 * product_type 只认 PROD/NPW；tech_code 须引用已存在的制程技术；
 * product_code 全局唯一建后不可改。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductAdminService {

    /** 行业通用产品类型（SiView：量产产品 / 陪片）。 */
    private static final Set<String> VALID_TYPES = Set.of("PROD", "NPW");

    /** 数据字典码（V7 种子）：产品分组 / 晶圆类型。 */
    public static final String DICT_PRODUCT_GROUP = "mds.product_group";
    public static final String DICT_WAFER_TYPE = "mds.wafer_type";

    private final CimProductRepository productRepository;
    private final CimTechnologyRepository technologyRepository;
    private final SysDictService dictService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record ProductView(String id, String productCode, String productName, String techCode,
                              String productType, String productGroup, String waferType,
                              String owner, String remark, Boolean enabled, String description,
                              Boolean occupied, String occupiedBy) {
    }

    public record ProductReq(String productCode, String productName, String techCode,
                             String productType, String productGroup, String waferType,
                             String owner, String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<ProductView> list(String keyword) {
        return productRepository.search(blankToNull(keyword)).stream()
                .map(ProductAdminService::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public ProductView create(ProductReq req) {
        requireText(req.productCode(), "productCode");
        requireText(req.productName(), "productName");
        if (productRepository.findByProductCodeAndDeletedFalse(req.productCode().trim()).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "产品编码已存在: " + req.productCode());
        }
        CimProduct entity = new CimProduct();
        apply(entity, req);
        return toView(productRepository.save(entity));
    }

    @Transactional
    public ProductView update(String id, ProductReq req) {
        CimProduct entity = requireProduct(id);
        requireText(req.productName(), "productName");
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // productCode 是全局唯一主标识（MES 按 code 引用）：建后不可改
        entity.setProductName(req.productName().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        applyOptional(entity, req);
        clearLock(entity);
        return toView(productRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimProduct entity = requireProduct(id);
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        entity.setDeleted(Boolean.TRUE);
        productRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimProduct requireProduct(String id) {
        return productRepository.findById(id)
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "产品不存在: " + id));
    }

    private void apply(CimProduct entity, ProductReq req) {
        entity.setProductCode(req.productCode().trim());
        entity.setProductName(req.productName().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        applyOptional(entity, req);
    }

    /** 分类/引用字段（create 与 update 共用同一套校验）。 */
    private void applyOptional(CimProduct entity, ProductReq req) {
        String type = req.productType() == null || req.productType().isBlank()
                ? "PROD" : req.productType().trim();
        if (!VALID_TYPES.contains(type)) {
            throw new BizException(BizCode.PARAM_INVALID, "productType 只支持 PROD/NPW: " + type);
        }
        entity.setProductType(type);
        // tech_code 引用守门：填了就必须是库内有效制程技术
        String techCode = blankToNull(req.techCode());
        if (techCode != null && technologyRepository.findByTechCodeAndDeletedFalse(techCode).isEmpty()) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "制程技术不存在: " + techCode);
        }
        entity.setTechCode(techCode);
        // 分类字段挂数据字典（V7）：落库存 itemKey，保存时校验必须在字典启用项内
        entity.setProductGroup(requireDictItem(DICT_PRODUCT_GROUP, req.productGroup(), "productGroup"));
        entity.setWaferType(requireDictItem(DICT_WAFER_TYPE, req.waferType(), "waferType"));
        entity.setOwner(blankToNull(req.owner()));
    }

    /** 字典值守门：非空值必须是该字典的启用 itemKey（防自由文本把分类打散）。 */
    private String requireDictItem(String dictCode, String value, String field) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        boolean hit = dictService.items(dictCode).stream().anyMatch(i -> v.equals(i.getItemKey()));
        if (!hit) {
            throw new BizException(BizCode.PARAM_INVALID,
                    field + " 不在数据字典 " + dictCode + " 内: " + v);
        }
        return v;
    }

    private static ProductView toView(CimProduct p) {
        return new ProductView(p.getId(), p.getProductCode(), p.getProductName(), p.getTechCode(),
                p.getProductType(), p.getProductGroup(), p.getWaferType(), p.getOwner(),
                p.getRemark(), p.getEnabled(), p.getDescription(), p.getOccupied(), p.getOccupiedBy());
    }

    /** 编辑锁守门：被他人占用即拒绝（与位置五层同口径）。 */
    private static void assertNotLockedByOther(boolean occupied, String occupiedBy) {
        if (occupied && occupiedBy != null && !occupiedBy.equals(EditLockSupport.currentUser())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "该记录正在被 " + occupiedBy + " 编辑，暂不能修改或删除");
        }
    }

    /** 保存成功后清编辑锁（与数据保存同一事务）。 */
    private static void clearLock(EditLockDefData entity) {
        entity.setOccupied(Boolean.FALSE);
        entity.setOccupiedBy(null);
        entity.setOccupiedAt(null);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
