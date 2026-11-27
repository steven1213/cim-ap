-- V36 (MySQL): Area 默认 Hold 处置部门。语义见 h2/V36__area_hold_dept.sql。

ALTER TABLE cim_area ADD COLUMN hold_dept VARCHAR(32);
