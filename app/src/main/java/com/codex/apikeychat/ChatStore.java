package com.codex.apikeychat;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ChatStore {
    private static final String PREFS = "chat_history";
    private static final String INDEX = "session_index";
    private static final String CURRENT = "current_session";
    private static final String SESSION_PREFIX = "session_";
    private static final String MIGRATED_SQLITE = "migrated_sqlite_v1";
    private static final String DB_NAME = "chat_history.db";
    private static final int DB_VERSION = 1;
    private static final int MAX_SESSIONS = 50;

    private final SharedPreferences prefs;
    private final Helper helper;

    ChatStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        helper = new Helper(context.getApplicationContext());
        migrateLegacyJsonIfNeeded();
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
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                "sessions",
                null,
                "id=?",
                new String[]{id},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            Session session = sessionFromCursor(cursor);
            session.messages = loadMessages(db, id);
            return session;
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
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.insertWithOnConflict("sessions", null, sessionValues(session), SQLiteDatabase.CONFLICT_REPLACE);
            db.delete("messages", "session_id=?", new String[]{session.id});
            for (int i = 0; i < session.messages.length(); i++) {
                JSONObject message = session.messages.optJSONObject(i);
                if (message == null) {
                    continue;
                }
                db.insert("messages", null, messageValues(session.id, i, message));
            }
            trimOldSessions(db);
            db.setTransactionSuccessful();
            prefs.edit().putString(CURRENT, session.id).apply();
        } catch (Exception ignored) {
        } finally {
            db.endTransaction();
        }
    }

    void delete(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("messages", "session_id=?", new String[]{id});
            db.delete("sessions", "id=?", new String[]{id});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (id.equals(prefs.getString(CURRENT, ""))) {
            prefs.edit().remove(CURRENT).apply();
        }
    }

    void setCurrentSessionId(String id) {
        prefs.edit().putString(CURRENT, id == null ? "" : id).apply();
    }

    List<SessionMeta> listSessions() {
        ArrayList<SessionMeta> sessions = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT s.id,s.title,s.updated_at,COUNT(m.id) AS message_count "
                + "FROM sessions s LEFT JOIN messages m ON s.id=m.session_id "
                + "GROUP BY s.id,s.title,s.updated_at "
                + "HAVING message_count>0 "
                + "ORDER BY s.updated_at DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                sessions.add(new SessionMeta(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        cursor.getInt(3)
                ));
            }
        } catch (Exception ignored) {
        }
        return sessions;
    }

    private void migrateLegacyJsonIfNeeded() {
        if (prefs.getBoolean(MIGRATED_SQLITE, false)) {
            return;
        }
        JSONArray index = legacyIndex();
        if (index.length() == 0) {
            prefs.edit().putBoolean(MIGRATED_SQLITE, true).apply();
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < index.length(); i++) {
                String id = index.optString(i, "");
                if (id.isEmpty()) {
                    continue;
                }
                String raw = prefs.getString(SESSION_PREFIX + id, "");
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                Session session = Session.fromJson(new JSONObject(raw));
                if (session.messages == null || session.messages.length() == 0) {
                    continue;
                }
                db.insertWithOnConflict("sessions", null, sessionValues(session), SQLiteDatabase.CONFLICT_REPLACE);
                db.delete("messages", "session_id=?", new String[]{session.id});
                for (int j = 0; j < session.messages.length(); j++) {
                    JSONObject message = session.messages.optJSONObject(j);
                    if (message != null) {
                        db.insert("messages", null, messageValues(session.id, j, message));
                    }
                }
            }
            trimOldSessions(db);
            db.setTransactionSuccessful();
            prefs.edit().putBoolean(MIGRATED_SQLITE, true).apply();
        } catch (Exception ignored) {
        } finally {
            db.endTransaction();
        }
    }

    private JSONArray legacyIndex() {
        try {
            return new JSONArray(prefs.getString(INDEX, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private ContentValues sessionValues(Session session) {
        ContentValues values = new ContentValues();
        values.put("id", session.id);
        values.put("title", emptyToDefault(session.title, "新聊天"));
        values.put("created_at", session.createdAt);
        values.put("updated_at", session.updatedAt);
        values.put("response_id", emptyToDefault(session.responseId, ""));
        values.put("last_model", emptyToDefault(session.lastModel, ""));
        values.put("api_mode", emptyToDefault(session.apiMode, ""));
        values.put("last_assistant_text", emptyToDefault(session.lastAssistantText, ""));
        values.put("last_user_prompt", emptyToDefault(session.lastUserPrompt, ""));
        values.put("transcript", emptyToDefault(session.transcript, ""));
        return values;
    }

    private ContentValues messageValues(String sessionId, int position, JSONObject message) {
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("position", position);
        values.put("role", message.optString("role", "assistant"));
        values.put("text", message.optString("text", ""));
        values.put("reasoning", message.optString("reasoning", ""));
        JSONArray sources = message.optJSONArray("sources");
        values.put("sources", sources == null ? "[]" : sources.toString());
        values.put("elapsed_ms", message.optLong("elapsedMs", 0L));
        values.put("time", message.optLong("time", System.currentTimeMillis()));
        return values;
    }

    private Session sessionFromCursor(Cursor cursor) {
        Session session = new Session();
        session.id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
        session.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        session.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        session.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        session.responseId = cursor.getString(cursor.getColumnIndexOrThrow("response_id"));
        session.lastModel = cursor.getString(cursor.getColumnIndexOrThrow("last_model"));
        session.apiMode = cursor.getString(cursor.getColumnIndexOrThrow("api_mode"));
        session.lastAssistantText = cursor.getString(cursor.getColumnIndexOrThrow("last_assistant_text"));
        session.lastUserPrompt = cursor.getString(cursor.getColumnIndexOrThrow("last_user_prompt"));
        session.transcript = cursor.getString(cursor.getColumnIndexOrThrow("transcript"));
        session.messages = new JSONArray();
        return session;
    }

    private JSONArray loadMessages(SQLiteDatabase db, String sessionId) {
        JSONArray messages = new JSONArray();
        try (Cursor cursor = db.query(
                "messages",
                null,
                "session_id=?",
                new String[]{sessionId},
                null,
                null,
                "position ASC, id ASC"
        )) {
            while (cursor.moveToNext()) {
                JSONObject message = new JSONObject();
                message.put("role", cursor.getString(cursor.getColumnIndexOrThrow("role")));
                message.put("text", cursor.getString(cursor.getColumnIndexOrThrow("text")));
                message.put("reasoning", cursor.getString(cursor.getColumnIndexOrThrow("reasoning")));
                String sources = cursor.getString(cursor.getColumnIndexOrThrow("sources"));
                JSONArray sourceArray;
                try {
                    sourceArray = new JSONArray(sources == null ? "[]" : sources);
                } catch (Exception ignored) {
                    sourceArray = new JSONArray();
                }
                if (sourceArray.length() > 0) {
                    message.put("sources", sourceArray);
                }
                long elapsedMs = cursor.getLong(cursor.getColumnIndexOrThrow("elapsed_ms"));
                if (elapsedMs > 0L) {
                    message.put("elapsedMs", elapsedMs);
                }
                message.put("time", cursor.getLong(cursor.getColumnIndexOrThrow("time")));
                messages.put(message);
            }
        } catch (Exception ignored) {
        }
        return messages;
    }

    private void trimOldSessions(SQLiteDatabase db) {
        ArrayList<String> ids = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT id FROM sessions ORDER BY updated_at DESC", null)) {
            while (cursor.moveToNext()) {
                ids.add(cursor.getString(0));
            }
        }
        if (ids.size() <= MAX_SESSIONS) {
            return;
        }
        List<String> toDelete = ids.subList(MAX_SESSIONS, ids.size());
        for (String id : new ArrayList<>(toDelete)) {
            db.delete("messages", "session_id=?", new String[]{id});
            db.delete("sessions", "id=?", new String[]{id});
        }
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null ? fallback : value;
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

    private static class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS sessions ("
                    + "id TEXT PRIMARY KEY,"
                    + "title TEXT NOT NULL,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "response_id TEXT,"
                    + "last_model TEXT,"
                    + "api_mode TEXT,"
                    + "last_assistant_text TEXT,"
                    + "last_user_prompt TEXT,"
                    + "transcript TEXT"
                    + ")");
            db.execSQL("CREATE TABLE IF NOT EXISTS messages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "session_id TEXT NOT NULL,"
                    + "position INTEGER NOT NULL,"
                    + "role TEXT NOT NULL,"
                    + "text TEXT,"
                    + "reasoning TEXT,"
                    + "sources TEXT,"
                    + "elapsed_ms INTEGER,"
                    + "time INTEGER,"
                    + "FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE"
                    + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_updated ON sessions(updated_at DESC)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_session_position ON messages(session_id, position)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }
}
