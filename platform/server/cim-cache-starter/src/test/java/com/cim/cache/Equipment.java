package com.cim.cache;

/** 测试用领域对象（POJO，便于验证缓存命中与序列化）。 */
public class Equipment {

    private String id;
    private String name;

    public Equipment() {
    }

    public Equipment(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Equipment e)) {
            return false;
        }
        return java.util.Objects.equals(id, e.id) && java.util.Objects.equals(name, e.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, name);
    }
}
