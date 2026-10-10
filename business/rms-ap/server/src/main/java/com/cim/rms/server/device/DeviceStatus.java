package com.cim.rms.server.device;

/** 设备状态（台账启停，区别于 GEM 设备运行状态）。 */
public enum DeviceStatus {
    /** 启用。 */
    ENABLED,
    /** 停用（不可被配方分发/比对选为目标机台）。 */
    DISABLED
}
