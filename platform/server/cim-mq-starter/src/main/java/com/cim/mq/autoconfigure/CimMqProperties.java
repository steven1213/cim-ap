package com.cim.mq.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cim-mq 配置（{@code cim.mq.*}）。
 *
 * <p>公共项（enabled/type/defaultTopic/consumer/idempotent/resilience/outbox）由框架统一消费；
 * 各 provider 专属项（kafka/pulsar/rabbit）仅对应 provider 读取。</p>
 */
@ConfigurationProperties(prefix = "cim.mq")
public class CimMqProperties {

    /** 总开关，默认关闭（按需开，见 design.md §2.6）。 */
    private boolean enabled = false;

    /** 当前 provider 类型：kafka | pulsar | rabbit。 */
    private String type = "kafka";

    /** 默认主题（未显式指定时的兜底）。 */
    private String defaultTopic = "cim-events";

    private Consumer consumer = new Consumer();
    private Idempotent idempotent = new Idempotent();
    private Resilience resilience = new Resilience();
    private Outbox outbox = new Outbox();

    private Kafka kafka = new Kafka();
    private Pulsar pulsar = new Pulsar();
    private Rabbit rabbit = new Rabbit();

    public static class Consumer {
        /** 消费最大重试次数（超出转死信）。 */
        private int maxAttempts = 3;
        /** 单消费者并发线程数。 */
        private int concurrency = 1;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int v) {
            this.maxAttempts = v;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int v) {
            this.concurrency = v;
        }
    }

    public static class Idempotent {
        private boolean enabled = true;
        /** 去重键 TTL（秒），过期后可重新处理（自愈）。 */
        private int ttlSec = 86400;
        /** 存储后端：redis | memory（无 Redis 时降级）。 */
        private String store = "redis";
        private String prefix = "mq:idem:";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }

        public int getTtlSec() {
            return ttlSec;
        }

        public void setTtlSec(int v) {
            this.ttlSec = v;
        }

        public String getStore() {
            return store;
        }

        public void setStore(String v) {
            this.store = v;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String v) {
            this.prefix = v;
        }
    }

    public static class Resilience {
        private int retryMaxAttempts = 5;
        private long retryInitialBackoffMs = 500;
        private double retryFactor = 2.0;
        private long retryMaxBackoffMs = 30000;
        private int cbFailureThreshold = 5;
        private long cbCooldownMs = 30000;

        public int getRetryMaxAttempts() {
            return retryMaxAttempts;
        }

        public void setRetryMaxAttempts(int v) {
            this.retryMaxAttempts = v;
        }

        public long getRetryInitialBackoffMs() {
            return retryInitialBackoffMs;
        }

        public void setRetryInitialBackoffMs(long v) {
            this.retryInitialBackoffMs = v;
        }

        public double getRetryFactor() {
            return retryFactor;
        }

        public void setRetryFactor(double v) {
            this.retryFactor = v;
        }

        public long getRetryMaxBackoffMs() {
            return retryMaxBackoffMs;
        }

        public void setRetryMaxBackoffMs(long v) {
            this.retryMaxBackoffMs = v;
        }

        public int getCbFailureThreshold() {
            return cbFailureThreshold;
        }

        public void setCbFailureThreshold(int v) {
            this.cbFailureThreshold = v;
        }

        public long getCbCooldownMs() {
            return cbCooldownMs;
        }

        public void setCbCooldownMs(long v) {
            this.cbCooldownMs = v;
        }
    }

    public static class Outbox {
        /** 是否启用发件箱（推荐开启，保证不丢）。 */
        private boolean enabled = true;
        /** 中继定时轮询开关。 */
        private boolean relayEnabled = true;
        private String table = "sys_outbox";
        private int batchSize = 100;
        private long intervalMs = 1000;
        /** 启动时自动建表（CREATE TABLE IF NOT EXISTS）。 */
        private boolean initSchema = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }

        public boolean isRelayEnabled() {
            return relayEnabled;
        }

        public void setRelayEnabled(boolean v) {
            this.relayEnabled = v;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String v) {
            this.table = v;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int v) {
            this.batchSize = v;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long v) {
            this.intervalMs = v;
        }

        public boolean isInitSchema() {
            return initSchema;
        }

        public void setInitSchema(boolean v) {
            this.initSchema = v;
        }
    }

    public static class Kafka {
        private String bootstrapServers = "localhost:9092";
        /** all = 全副本确认，最高不丢保障。 */
        private String acks = "all";
        private int retries = 10;
        /** 幂等生产者（去重生产端重发）。 */
        private boolean enableIdempotence = true;
        private Map<String, String> extras = new LinkedHashMap<>();

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String v) {
            this.bootstrapServers = v;
        }

        public String getAcks() {
            return acks;
        }

        public void setAcks(String v) {
            this.acks = v;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int v) {
            this.retries = v;
        }

        public boolean isEnableIdempotence() {
            return enableIdempotence;
        }

        public void setEnableIdempotence(boolean v) {
            this.enableIdempotence = v;
        }

        public Map<String, String> getExtras() {
            return extras;
        }

        public void setExtras(Map<String, String> v) {
            this.extras = v;
        }
    }

    public static class Pulsar {
        private String serviceUrl = "pulsar://localhost:6650";
        private boolean enableBatching = true;
        private Map<String, String> extras = new LinkedHashMap<>();

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String v) {
            this.serviceUrl = v;
        }

        public boolean isEnableBatching() {
            return enableBatching;
        }

        public void setEnableBatching(boolean v) {
            this.enableBatching = v;
        }

        public Map<String, String> getExtras() {
            return extras;
        }

        public void setExtras(Map<String, String> v) {
            this.extras = v;
        }
    }

    public static class Rabbit {
        private String addresses = "localhost:5672";
        private String username = "guest";
        private String password = "guest";
        private String virtualHost = "/";
        /** 默认交换机名（topic 类型）。 */
        private String exchange = "cim-mq-exchange";

        public String getAddresses() {
            return addresses;
        }

        public void setAddresses(String v) {
            this.addresses = v;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String v) {
            this.username = v;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String v) {
            this.password = v;
        }

        public String getVirtualHost() {
            return virtualHost;
        }

        public void setVirtualHost(String v) {
            this.virtualHost = v;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String v) {
            this.exchange = v;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        this.enabled = v;
    }

    public String getType() {
        return type;
    }

    public void setType(String v) {
        this.type = v;
    }

    public String getDefaultTopic() {
        return defaultTopic;
    }

    public void setDefaultTopic(String v) {
        this.defaultTopic = v;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer v) {
        this.consumer = v;
    }

    public Idempotent getIdempotent() {
        return idempotent;
    }

    public void setIdempotent(Idempotent v) {
        this.idempotent = v;
    }

    public Resilience getResilience() {
        return resilience;
    }

    public void setResilience(Resilience v) {
        this.resilience = v;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox v) {
        this.outbox = v;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka v) {
        this.kafka = v;
    }

    public Pulsar getPulsar() {
        return pulsar;
    }

    public void setPulsar(Pulsar v) {
        this.pulsar = v;
    }

    public Rabbit getRabbit() {
        return rabbit;
    }

    public void setRabbit(Rabbit v) {
        this.rabbit = v;
    }
}
