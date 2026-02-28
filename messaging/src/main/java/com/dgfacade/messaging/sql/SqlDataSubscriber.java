/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.sql;

import com.dgfacade.messaging.core.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * SQL-based DataSubscriber. Polls database table at configurable intervals.
 * Reads messages with status='PENDING' and marks them 'PROCESSING' then 'DONE'.
 * Schedule-driven with configurable polling interval.
 */
public class SqlDataSubscriber extends AbstractSubscriber {

    private DataSource dataSource;
    private String tableName = "dg_messages";
    private int pollIntervalSeconds = 30;
    private int batchSize = 100;
    private ScheduledExecutorService pollScheduler;

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void doConnect() {
        tableName = (String) config.getOrDefault("table_name", "dg_messages");
        pollIntervalSeconds = (int) config.getOrDefault("poll_interval_seconds", 30);
        batchSize = (int) config.getOrDefault("batch_size", 100);
        state = ConnectionState.CONNECTED;
        log.info("SQL subscriber connected, table: {}, poll interval: {}s", tableName, pollIntervalSeconds);
    }

    @Override
    public void start() {
        super.start();
        pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sql-poll");
            t.setDaemon(true);
            return t;
        });
        pollScheduler.scheduleAtFixedRate(this::pollTable, 0, pollIntervalSeconds, TimeUnit.SECONDS);
    }

    private void pollTable() {
        if (paused || internalQueue.size() >= backpressureMaxDepth) return;
        Set<String> topics = listeners.keySet();
        if (topics.isEmpty()) return;

        String placeholders = String.join(",", Collections.nCopies(topics.size(), "?"));
        String selectSql = "SELECT id, topic, payload, created_at FROM " + tableName +
                " WHERE status='PENDING' AND topic IN (" + placeholders + ") ORDER BY created_at LIMIT " + batchSize;
        String updateSql = "UPDATE " + tableName + " SET status='DONE' WHERE id=?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement select = conn.prepareStatement(selectSql)) {
            int idx = 1;
            for (String t : topics) select.setString(idx++, t);
            ResultSet rs = select.executeQuery();
            while (rs.next()) {
                if (internalQueue.size() >= backpressureMaxDepth) break;
                MessageEnvelope envelope = new MessageEnvelope(rs.getString("topic"), rs.getString("payload"));
                envelope.setMessageId(rs.getString("id"));
                envelope.setTimestamp(rs.getTimestamp("created_at").toInstant());
                enqueue(envelope);
                try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                    update.setString(1, envelope.getMessageId());
                    update.executeUpdate();
                }
            }
        } catch (SQLException e) {
            log.error("SQL poll error", e);
        }
    }

    @Override
    protected void doSubscribe(String topicOrQueue) { /* registered in listeners map */ }

    @Override
    protected void doUnsubscribe(String topicOrQueue) { /* removed from listeners map */ }

    @Override
    protected void doDisconnect() {
        if (pollScheduler != null) pollScheduler.shutdownNow();
    }

    @Override
    public boolean isConnected() { return state == ConnectionState.CONNECTED && dataSource != null; }
}
