package com.cim.jpa.history;

/**
 * 历史操作类型（对应 {@code BaseHistoryData.opType}）。
 */
public enum OpType {

    /** 插入。 */
    INSERT("I"),

    /** 更新。 */
    UPDATE("U"),

    /** 删除。 */
    DELETE("D");

    private final String code;

    OpType(String code) {
        this.code = code;
    }

    /** 落库短码。 */
    public String code() {
        return code;
    }
}
