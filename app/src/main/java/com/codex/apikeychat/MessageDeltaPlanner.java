package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class MessageDeltaPlanner {
    private MessageDeltaPlanner() {
    }

    static Plan plan(JSONArray stored, JSONArray desired) {
        JSONArray oldMessages = stored == null ? new JSONArray() : stored;
        JSONArray newMessages = desired == null ? new JSONArray() : desired;
        ArrayList<Integer> inserts = new ArrayList<>();
        ArrayList<Integer> updates = new ArrayList<>();
        int shared = Math.min(oldMessages.length(), newMessages.length());
        for (int i = 0; i < shared; i++) {
            if (messagesDiffer(oldMessages.optJSONObject(i), newMessages.optJSONObject(i))) {
                updates.add(i);
            }
        }
        for (int i = shared; i < newMessages.length(); i++) {
            inserts.add(i);
        }
        int deleteFrom = oldMessages.length() > newMessages.length() ? newMessages.length() : -1;
        return new Plan(inserts, updates, deleteFrom);
    }

    static boolean messagesDiffer(JSONObject left, JSONObject right) {
        return !canonical(left).equals(canonical(right));
    }

    private static String canonical(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            ArrayList<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) {
                keys.add(iterator.next());
            }
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) {
                appendToken(result, key);
                result.append(':').append(canonical(object.opt(key))).append(';');
            }
            return result.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                result.append(canonical(array.opt(i))).append(';');
            }
            return result.append(']').toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        StringBuilder result = new StringBuilder();
        appendToken(result, String.valueOf(value));
        return result.toString();
    }

    private static void appendToken(StringBuilder target, String value) {
        String safe = value == null ? "" : value;
        target.append(safe.length()).append('#').append(safe);
    }

    static final class Plan {
        final List<Integer> insertPositions;
        final List<Integer> updatePositions;
        final int deleteFromPosition;

        Plan(List<Integer> insertPositions, List<Integer> updatePositions, int deleteFromPosition) {
            this.insertPositions = insertPositions;
            this.updatePositions = updatePositions;
            this.deleteFromPosition = deleteFromPosition;
        }
    }
}
