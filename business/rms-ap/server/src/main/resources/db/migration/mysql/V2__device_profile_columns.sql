-- RMS W2：设备台账扩展列（Req 46/48 增强）
-- 设备类型补制造商/型号；设备台账补 IP。备注统一走既有 description 列。

ALTER TABLE device_type ADD COLUMN manufacturer VARCHAR(128);
ALTER TABLE device_type ADD COLUMN model VARCHAR(128);

ALTER TABLE device ADD COLUMN ip VARCHAR(64);
