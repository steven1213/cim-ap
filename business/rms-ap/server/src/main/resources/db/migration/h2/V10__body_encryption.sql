-- RMS W8：D9 Body 加密（库内密文存储，AES-256-GCM，密钥走 rms.crypto.body-key 不入库）。
-- body_enc：0=明文（存量兼容）/ 1=AES-GCM 密文（IV[12] || ciphertext+tag 的 Base64）。
-- body_hash 语义不变：始终对明文字节计算（IF-COM 完整性红线）。存量数据不回填加密，读取兼容两种。

ALTER TABLE recipe_version ADD COLUMN body_enc INT NOT NULL DEFAULT 0;
