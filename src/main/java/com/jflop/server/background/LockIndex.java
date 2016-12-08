package com.jflop.server.background;

import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Stores locks shared by multiple cluster nodes.<br/>
 *
 * @author artem on 12/7/16.
 */
@Component
public class LockIndex extends IndexTemplate {

    private static final String LOCK_INDEX_NAME = "jf-lock";

    private List<PersistentData<TaskLockData>> cache;
    private long retrievedAt;

    public LockIndex() {
        super(LOCK_INDEX_NAME + "-template", LOCK_INDEX_NAME + "*",
                new DocType("task", "persistency/taskLockData.json", TaskLockData.class));
    }

    @Override
    public String indexName() {
        return LOCK_INDEX_NAME;
    }

    public void createTaskLock(TaskLockData taskLockData) {
        createDocumentIfNotExists(new PersistentData<>(taskLockData.lockId, 0, taskLockData));
    }

    public void deleteTaskLock(TaskLockData taskLockData) {
        deleteDocument(new PersistentData(taskLockData.lockId, 0, taskLockData));
    }

    public Collection<TaskLockData> getVacantLocks(String taskName) {
        Collection<TaskLockData> res = new ArrayList<>();
        for (PersistentData<TaskLockData> doc : getAllVacantLocks()) {
            TaskLockData lock = doc.source;
            if (lock.taskName.equals(taskName)) res.add(lock);
        }
        return res;
    }

    public boolean obtainLock(TaskLockData lock, long lockUntil) {
        PersistentData<TaskLockData> doc = getDocument(lock.lockId);
        if (doc != null) {
            doc.source.lockedUntil = new Date(lockUntil);
            try {
                PersistentData<TaskLockData> persistentData = updateDocument(doc);
                return persistentData.version > doc.version;
            } catch (VersionConflictEngineException e) {
                // someone has already updated the lock, it's ok - do nothing
            }
        }
        return false;
    }

    public void releaseLock(TaskLockData lock) {
        lock.lockedUntil = new Date(0);
        // ignore the version, because the cache might be outdated
        try {
            updateDocument(new PersistentData<>(lock.lockId, 0, lock));
        } catch (DocumentMissingException e) {
            // the task might have been stopped, it's ok
        }
    }

    private PersistentData<TaskLockData> getDocument(String lockId) {
        for (PersistentData<TaskLockData> doc : getAllVacantLocks()) {
            TaskLockData source = doc.source;
            if (lockId.equals(source.lockId)) return doc;
        }
        return null;
    }

    private synchronized List<PersistentData<TaskLockData>> getAllVacantLocks() {
        long now = System.currentTimeMillis();
        if (cache == null || (now - retrievedAt) > 1000) {
            QueryBuilder query = QueryBuilders.rangeQuery("lockedUntil").lte(new Date(now));
            retrievedAt = now;
            cache = find(query, 10000, TaskLockData.class); // what if there are more docs than maxHits?
        }
        return cache;
    }
}
