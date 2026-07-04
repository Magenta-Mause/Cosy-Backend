package com.magentamause.cosybackend.services.core.gameserver;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;

@Component
class GameServerMountLocks {

    private final ConcurrentHashMap<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

    ReadWriteLock lockForServer(String serverUuid) {
        return locks.computeIfAbsent(serverUuid, k -> new ReentrantReadWriteLock(true));
    }

    <T> T withReadLock(String serverUuid, java.util.concurrent.Callable<T> action) {
        Lock l = lockForServer(serverUuid).readLock();
        l.lock();
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            l.unlock();
        }
    }

    void withWriteLock(String serverUuid, Runnable action) {
        Lock l = lockForServer(serverUuid).writeLock();
        l.lock();
        try {
            action.run();
        } finally {
            l.unlock();
        }
    }
}
