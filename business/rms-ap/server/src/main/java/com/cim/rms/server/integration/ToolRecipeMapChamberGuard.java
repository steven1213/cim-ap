package com.cim.rms.server.integration;

import com.cim.rms.server.device.DeviceAdminService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 腔体引用校验（实现 {@link DeviceAdminService.ChamberRefGuard}）。
 *
 * <p>放在 integration 包是为了避免 {@code device → integration} 的包循环依赖：
 * 设备域只声明窄接口，由已同时依赖两域的 integration 侧实现。</p>
 */
@Component
@RequiredArgsConstructor
public class ToolRecipeMapChamberGuard implements DeviceAdminService.ChamberRefGuard {

    private final ToolRecipeMapRepository repository;

    @Override
    public void assertChamberUnreferenced(String chamberId) {
        var refs = repository.findByChamberIdAndDeletedFalse(chamberId);
        if (!refs.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "腔体仍被 PPID 映射引用，禁止删除（引用数 " + refs.size() + "）");
        }
    }
}
