package com.cim.mq.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 基于 JDBC 的发件箱存储（默认实现）。
 *
 * <p>表 {@code sys_outbox} 由 {@code initSchema=true} 时自动建表（CREATE TABLE IF NOT EXISTS），
 * 亦可交 Flyway 管理（见 {@code db/migration}）。claimBatch 采用无锁 SELECT，
 * 多中继实例的重复投递由<b>消费幂等</b>兜底（见 §16）。</p>
 */
public class JdbcOutboxStore implements OutboxStore {

    private final JdbcTemplate jdbc;
    private final String table;

    public JdbcOutboxStore(DataSource dataSource, String table, boolean initSchema) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.table = table;
        if (initSchema) {
            initSchema();
        }
    }

    private void initSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "id VARCHAR(64) PRIMARY KEY,"
                + "aggregate_type VARCHAR(128),"
                + "aggregate_id VARCHAR(128),"
                + "event_type VARCHAR(128),"
                + "topic VARCHAR(256),"
                + "payload BLOB,"
                + "status VARCHAR(16) NOT NULL,"
                + "attempt_count INT NOT NULL DEFAULT 0,"
                + "create_time TIMESTAMP NOT NULL,"
                + "next_retry_at TIMESTAMP NOT NULL,"
                + "last_error VARCHAR(1024))";
        jdbc.execute(ddl);
    }

    @Override
    public void save(OutboxRecord record) {
        jdbc.update(
                "INSERT INTO " + table
                        + " (id, aggregate_type, aggregate_id, event_type, topic, payload, status, attempt_count, create_time, next_retry_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?)",
                record.id(), record.aggregateType(), record.aggregateId(), record.eventType(),
                record.topic(), record.payload(), record.status().name(), record.attemptCount(),
                Timestamp.from(record.createTime()), Timestamp.from(record.nextRetryAt()));
    }

    @Override
    public List<OutboxRecord> claimBatch(int limit) {
        return jdbc.query(
                "SELECT id, aggregate_type, aggregate_id, event_type, topic, payload, status,"
                        + " attempt_count, create_time, next_retry_at, last_error FROM " + table
                        + " WHERE status='PENDING' AND next_retry_at <= ? ORDER BY create_time ASC LIMIT ?",
                ps -> {
                    ps.setTimestamp(1, Timestamp.from(Instant.now()));
                    ps.setInt(2, limit);
                },
                ROW_MAPPER);
    }

    @Override
    public void markSent(String id) {
        jdbc.update("UPDATE " + table + " SET status='SENT' WHERE id=?", id);
        jdbc.update("DELETE FROM " + table + " WHERE id=?", id);
    }

    @Override
    public void markFailed(String id, int attemptCount, String error, Instant nextRetryAt) {
        jdbc.update("UPDATE " + table + " SET status='PENDING', attempt_count=?, last_error=?, next_retry_at=? WHERE id=?",
                attemptCount, error, Timestamp.from(nextRetryAt), id);
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM " + table + " WHERE id=?", id);
    }

    private static final RowMapper<OutboxRecord> ROW_MAPPER = (ResultSet rs, int rowNum) -> {
        OutboxRecord r = new OutboxRecord();
        r.id = rs.getString("id");
        r.aggregateType = rs.getString("aggregate_type");
        r.aggregateId = rs.getString("aggregate_id");
        r.eventType = rs.getString("event_type");
        r.topic = rs.getString("topic");
        r.payload = rs.getBytes("payload");
        r.status = OutboxRecord.Status.valueOf(rs.getString("status"));
        r.attemptCount = rs.getInt("attempt_count");
        r.createTime = rs.getTimestamp("create_time").toInstant();
        r.nextRetryAt = rs.getTimestamp("next_retry_at").toInstant();
        r.lastError = rs.getString("last_error");
        return r;
    };
}
