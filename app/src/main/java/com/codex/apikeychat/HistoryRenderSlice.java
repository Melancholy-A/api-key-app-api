package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;

final class HistoryRenderSlice {
    final JSONArray items;
    final int startIndex;
    final int remainingCount;

    HistoryRenderSlice(JSONArray items, int startIndex, int remainingCount) {
        this.items = items == null ? new JSONArray() : items;
        this.startIndex = Math.max(0, startIndex);
        this.remainingCount = Math.max(0, remainingCount);
    }

    static HistoryRenderSlice from(JSONArray messages, int maxItems) {
        JSONArray source = messages == null ? new JSONArray() : messages;
        int count = Math.max(1, maxItems);
        int start = Math.max(0, source.length() - count);
        JSONArray items = new JSONArray();
        for (int i = start; i < source.length(); i++) {
            JSONObject message = source.optJSONObject(i);
            if (message != null) {
                items.put(message);
            }
        }
        return new HistoryRenderSlice(items, start, start);
    }
}
