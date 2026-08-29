package com.codex.apikeychat;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ChatRestoreCapacityPolicyTest {
    @Test
    public void restoreRejectsNewIdsThatWouldEvictUnrelatedLocalChats() throws Exception {
        Set<String> localIds = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            localIds.add("local-" + i);
        }

        Method method;
        try {
            method = ChatStore.class.getDeclaredMethod(
                    "requireRestoreCapacity",
                    Set.class,
                    Iterable.class
            );
        } catch (NoSuchMethodException error) {
            fail("ChatStore must validate restore capacity before writing");
            return;
        }
        method.setAccessible(true);

        try {
            method.invoke(null, localIds, Arrays.asList("local-1", "backup-new"));
            fail("restore that exceeds 50 sessions should be rejected");
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            assertTrue(cause instanceof IllegalStateException);
            assertTrue(cause.getMessage().contains("50"));
        }
    }

    @Test
    public void replacingExistingIdsDoesNotConsumeAdditionalCapacity() throws Exception {
        Method method = ChatStore.class.getDeclaredMethod(
                "requireRestoreCapacity",
                Set.class,
                Iterable.class
        );
        method.setAccessible(true);

        Object result = method.invoke(
                null,
                new HashSet<>(Arrays.asList("one", "two")),
                Arrays.asList("one", "two")
        );

        assertEquals(null, result);
    }
}
