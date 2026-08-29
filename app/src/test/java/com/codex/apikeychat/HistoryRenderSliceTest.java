package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HistoryRenderSliceTest {
    @Test
    public void renderSliceKeepsOnlyTheLatestTwentyMessagesForTheUi() throws Exception {
        JSONArray messages = new JSONArray();
        for (int i = 0; i < 100000; i++) {
            messages.put(new JSONObject().put("text", "m" + i));
        }

        HistoryRenderSlice slice = HistoryRenderSlice.from(messages, 20);

        assertEquals(99980, slice.startIndex);
        assertEquals(99980, slice.remainingCount);
        assertEquals(20, slice.items.length());
        assertEquals("m99980", slice.items.getJSONObject(0).getString("text"));
        assertEquals("m99999", slice.items.getJSONObject(19).getString("text"));
    }
}
