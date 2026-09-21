package com.cim.jpa.config;

import com.cim.jpa.id.IdMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 持久化配置（{@code cim.jpa.*}，见 design.md §5 配置键表）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cim.jpa")
public class CimJpaProperties {

    /** 主键生成。 */
    private Id id = new Id();

    /** 历史记录。 */
    private History history = new History();

    /** 命名策略。 */
    private Naming naming = new Naming();

    /** 主键生成配置。 */
    @Getter
    @Setter
    public static class Id {
        /** 生成策略。 */
        private IdMode mode = IdMode.SNOWFLAKE;
        /** 雪花 workerId（应由部署平台下发，见 README §3.1）。 */
        private long workerId = 1L;
        /** 雪花 datacenterId。 */
        private long datacenterId = 1L;
    }

    /** 历史记录配置。 */
    @Getter
    @Setter
    public static class History {
        /** 是否启用自动历史记录。 */
        private boolean enabled = true;
        /** 历史写入模式（关键数据同事务，高频走发件箱异步，见 README §12）。 */
        private HistoryWriteMode writeMode = HistoryWriteMode.IN_TRANSACTION;
    }

    /** 命名策略配置。 */
    @Getter
    @Setter
    public static class Naming {
        /** 物理命名策略：统一小写蛇形（跨库一致，见 README §3）。 */
        private boolean lowercaseSnake = true;
        /** 全局表名前缀（可选）。 */
        private String tablePrefix = "";
    }

    /** 历史写入模式。 */
    public enum HistoryWriteMode {
        /** 与业务同事务写入（强一致，适用关键数据）。 */
        IN_TRANSACTION,
        /** 经事务发件箱异步写入（适用高频数据，失败不影响业务）。 */
        OUTBOX
    }
}
