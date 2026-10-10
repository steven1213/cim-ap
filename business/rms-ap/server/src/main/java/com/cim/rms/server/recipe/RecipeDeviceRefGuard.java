package com.cim.rms.server.recipe;

import com.cim.rms.server.device.DeviceAdminService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 设备删除引用守卫（配方侧实现，避免 device → recipe 循环依赖）。
 *
 * <p>W1：配方只挂机型（{@code Recipe.deviceTypeId}），不直接引用具体设备 → 暂无拦截项。
 * W2 接入「用户↔设备授权（Req 11）」与上传历史（IF-E1 以设备为锚点）后，在此补：
 * 存在上传历史或授权记录的设备禁止删除。</p>
 */
@Component
@RequiredArgsConstructor
public class RecipeDeviceRefGuard implements DeviceAdminService.RecipeRefGuard {

    @Override
    public void assertDeviceUnreferenced(String deviceId) {
        // W1 无设备级引用；见类注释（W2 补上传历史/授权校验）
    }
}
