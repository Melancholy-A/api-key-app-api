package com.codex.apikeychat;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SerialTaskExecutorTest {
    @Test
    public void submittedWorkRunsOffTheCallingThread() throws Exception {
        Class<?> executorClass;
        try {
            executorClass = Class.forName("com.codex.apikeychat.SerialTaskExecutor");
        } catch (ClassNotFoundException error) {
            fail("SerialTaskExecutor must run backup work off the UI thread");
            return;
        }
        Constructor<?> constructor = executorClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        Object executor = constructor.newInstance("chat-backup-test");
        Method execute = executorClass.getDeclaredMethod("execute", Runnable.class);
        Method shutdown = executorClass.getDeclaredMethod("shutdown");
        execute.setAccessible(true);
        shutdown.setAccessible(true);

        Thread caller = Thread.currentThread();
        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        execute.invoke(executor, (Runnable) () -> {
            worker.set(Thread.currentThread());
            finished.countDown();
        });

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertNotEquals(caller, worker.get());
        shutdown.invoke(executor);
    }
}
