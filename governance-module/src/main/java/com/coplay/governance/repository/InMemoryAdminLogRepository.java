package com.coplay.governance.repository;

import com.coplay.governance.domain.AdminLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory administrator audit log used by the standalone demo and tests.
 */
public final class InMemoryAdminLogRepository implements AdminLogRepository {

    private final List<AdminLog> logs = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong(7000);

    @Override
    public long nextId() {
        return ids.incrementAndGet();
    }

    @Override
    public AdminLog save(AdminLog log) {
        logs.add(log);
        return log;
    }

    @Override
    public List<AdminLog> findAll() {
        return List.copyOf(logs);
    }
}
