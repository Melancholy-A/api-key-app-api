package com.codex.apikeychat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class ChatStore {
    private static final String PREFS = "chat_history";
    private static final String INDEX = "session_index";
    private static final String CURRENT = "current_session";
    private static final String SESSION_PREFIX = "session_";
    private static final int MAX_SESSIONS = 50;

    private final SharedPreferences prefs;

    ChatStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Session createSession() {
        long now = System.currentTimeMillis();
        Session session = new Session();
        session.id = "s" + now;
        session.title = "新聊天";
        session.createdAt = now;
        session.updatedAt = now;
        session.messages = new JSONArray();
        return session;
    }

    Session loadCurrentOrCreate() {
        String id = prefs.getString(CURRENT, "");
        Session session = id == null || id.isEmpty() ? null : load(id);
        return session == null ? createSession() : session;
    }

    Session load(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String raw = prefs.getString(SESSION_PREFIX + id, "");
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Session.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    void save(Session session) {
        if (session == null || session.id == null || session.id.isEmpty()) {
            return;
        }
        if (session.messages == null || session.messages.length() == 0) {
            delete(session.id);
            return;
        }
        session.updatedAt = System.currentTimeMillis();
        prefs.edit()
                .putString(SESSION_PREFIX + session.id, session.toJson().toString())
                .putString(INDEX, updateIndex(session.id).toString())
                .putString(CURRENT, session.id)
                .apply();
    }

    void delete(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        JSONArray index = index();
        JSONArray next = new JSONArray();
        for (int i = 0; i < index.length(); i++) {
            String value = index.optString(i, "");
            if (!id.equals(value)) {
                next.put(value);
            }
        }
        SharedPreferences.Editor editor = prefs.edit()
                .remove(SESSION_PREFIX + id)
                .putString(INDEX, next.toString());
        if (id.equals(prefs.getString(CURRENT, ""))) {
            editor.remove(CURRENT);
        }
        editor.apply();
    }

    void setCurrentSessionId(String id) {
        prefs.edit().putString(CURRENT, id).apply();
    }

    List<SessionMeta> listSessions() {
        JSONArray index = index();
        ArrayList<SessionMeta> sessions = new ArrayList<>();
        JSONArray kept = new JSONArray();
        boolean changed = false;
        for (int i = 0; i < index.length(); i++) {
            String id = index.optString(i, "");
            Session session = load(id);
            if (session != null && session.messages != null && session.messages.length() > 0) {
                kept.put(id);
                sessions.add(new SessionMeta(session.id, session.title, session.updatedAt, session.messages.length()));
            } else if (!id.isEmpty()) {
                changed = true;
                prefs.edit().remove(SESSION_PREFIX + id).apply();
            }
        }
        if (changed) {
            prefs.edit().putString(INDEX, kept.toString()).apply();
        }
        Collections.sort(sessions, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return sessions;
    }

    private JSONArray updateIndex(String id) {
        JSONArray old = index();
        JSONArray next = new JSONArray();
        next.put(id);
        for (int i = 0; i < old.length() && next.length() < MAX_SESSIONS; i++) {
            String value = old.optString(i, "");
            if (!id.equals(value) && !value.isEmpty()) {
                next.put(value);
            }
        }
        return next;
    }

    private JSONArray index() {
        try {
            return new JSONArray(prefs.getString(INDEX, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    static class SessionMeta {
        final String id;
        final String title;
        final long updatedAt;
        final int count;

        SessionMeta(String id, String title, long updatedAt, int count) {
            this.id = id;
            this.title = title;
            this.updatedAt = updatedAt;
            this.count = count;
        }

        String label() {
            String value = title == null || title.isEmpty() ? "新聊天" : title;
            return value + " · " + count + " 条";
        }
    }

    static class Session {
        String id = "";
        String title = "新聊天";
        long createdAt;
        long updatedAt;
        String responseId = "";
        String lastModel = "";
        String apiMode = "";
        String lastAssistantText = "";
        String lastUserPrompt = "";
        String transcript = "";
        JSONArray messages = new JSONArray();

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("title", title);
                json.put("createdAt", createdAt);
                json.put("updatedAt", updatedAt);
                json.put("responseId", responseId);
                json.put("lastModel", lastModel);
                json.put("apiMode", apiMode);
                json.put("lastAssistantText", lastAssistantText);
                json.put("lastUserPrompt", lastUserPrompt);
                json.put("transcript", transcript);
                json.put("messages", messages == null ? new JSONArray() : messages);
            } catch (Exception ignored) {
            }
            return json;
        }

        static Session fromJson(JSONObject json) {
            Session session = new Session();
            session.id = json.optString("id", "");
            session.title = json.optString("title", "新聊天");
            session.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            session.updatedAt = json.optLong("updatedAt", session.createdAt);
            session.responseId = json.optString("responseId", "");
            session.lastModel = json.optString("lastModel", "");
            session.apiMode = json.optString("apiMode", "");
            session.lastAssistantText = json.optString("lastAssistantText", "");
            session.lastUserPrompt = json.optString("lastUserPrompt", "");
            session.transcript = json.optString("transcript", "");
            session.messages = json.optJSONArray("messages");
            if (session.messages == null) {
                session.messages = new JSONArray();
            }
            return session;
        }
    }
}
