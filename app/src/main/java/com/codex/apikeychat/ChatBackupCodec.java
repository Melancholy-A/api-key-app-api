package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class ChatBackupCodec {
    static final String FORMAT = "codex-mobile-chat-backup";
    static final int SCHEMA_VERSION = 1;
    static final int MAX_BACKUP_SESSIONS = 50;
    static final int MAX_BACKUP_MESSAGES = 100000;
    static final int MAX_BACKUP_BYTES = 16 * 1024 * 1024;
    private static final Pattern DATA_IMAGE_PATTERN = Pattern.compile(
            "data:image/[^\\s)]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LOCAL_IMAGE_MARKDOWN_PATTERN = Pattern.compile(
            "!\\[[^\\]]*]\\((?:file|content):[^)]*\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REMOTE_GENERATED_IMAGE_MARKDOWN_PATTERN = Pattern.compile(
            "!\\[[^\\]]*(?:生成图片|generated\\s*image)[^\\]]*]\\(https?://[^)]*\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final String[] BRANCH_METADATA_KEYS = {
            "node_id",
            "parent_id",
            "active_child_id",
            "nodeId",
            "parentNodeId",
            "selectedChildNodeId"
    };

    private ChatBackupCodec() {
    }

    static String encode(
            List<ChatStore.Session> sessions,
            String currentSessionId,
            long exportedAt,
            String appVersion
    ) throws BackupException {
        try {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("appVersion", safe(appVersion));
            root.put("exportedAt", Math.max(0L, exportedAt));
            root.put("currentSessionId", safe(currentSessionId));
            JSONArray values = new JSONArray();
            int messageCount = 0;
            if (sessions != null) {
                for (ChatStore.Session session : sessions) {
                    if (session != null && session.messages != null && session.messages.length() > 0) {
                        if (values.length() >= MAX_BACKUP_SESSIONS) {
                            throw new BackupException("备份聊天数量超过 " + MAX_BACKUP_SESSIONS + " 个");
                        }
                        JSONObject backup = backupSession(session);
                        messageCount += backup.getJSONArray("messages").length();
                        if (messageCount > MAX_BACKUP_MESSAGES) {
                            throw new BackupException("备份消息数量超过 " + MAX_BACKUP_MESSAGES + " 条");
                        }
                        values.put(backup);
                    }
                }
            }
            root.put("sessions", values);
            String encoded = root.toString(2);
            if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BACKUP_BYTES) {
                throw new BackupException("备份文件过大，最多 " + (MAX_BACKUP_BYTES / (1024 * 1024)) + " MiB");
            }
            return encoded;
        } catch (BackupException error) {
            throw error;
        } catch (Exception error) {
            throw new BackupException("无法生成聊天备份", error);
        }
    }

    static RestorePlan decode(String raw) throws BackupException {
        try {
            JSONObject root = new JSONObject(raw == null ? "" : raw.trim());
            if (!FORMAT.equals(root.optString("format", ""))) {
                throw new BackupException("不是 Codex Mobile 聊天备份格式");
            }
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
                throw new BackupException("不支持的聊天备份版本");
            }
            JSONArray sourceSessions = root.optJSONArray("sessions");
            if (sourceSessions == null) {
                throw new BackupException("备份中缺少聊天记录");
            }
            if (sourceSessions.length() > MAX_BACKUP_SESSIONS) {
                throw new BackupException("备份包含的聊天数量超过 " + MAX_BACKUP_SESSIONS + " 个");
            }

            ArrayList<ChatStore.Session> sessions = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            int messageCount = 0;
            for (int i = 0; i < sourceSessions.length(); i++) {
                JSONObject sessionJson = sourceSessions.optJSONObject(i);
                if (sessionJson == null) {
                    throw new BackupException("第 " + (i + 1) + " 个聊天记录损坏");
                }
                validateSessionStructure(sessionJson, i);
                ChatStore.Session session = ChatStore.Session.fromJson(
                        new JSONObject(sessionJson.toString())
                );
                validateSession(session, i);
                if (!ids.add(session.id)) {
                    throw new BackupException("备份中存在重复聊天 ID：" + session.id);
                }
                messageCount += session.messages.length();
                if (messageCount > MAX_BACKUP_MESSAGES) {
                    throw new BackupException("备份消息数量过多");
                }
                sessions.add(session);
            }

            String currentSessionId = root.optString("currentSessionId", "");
            if (!ids.contains(currentSessionId)) {
                currentSessionId = sessions.isEmpty() ? "" : sessions.get(0).id;
            }
            return new RestorePlan(
                    sessions,
                    currentSessionId,
                    messageCount,
                    root.optLong("exportedAt", 0L),
                    root.optString("appVersion", "")
            );
        } catch (BackupException error) {
            throw error;
        } catch (Exception error) {
            throw new BackupException("聊天备份 JSON 无法解析", error);
        }
    }

    private static void validateSessionStructure(JSONObject session, int index) throws BackupException {
        if (!session.has("id") || session.isNull("id") || !(session.opt("id") instanceof String)) {
            throw new BackupException("第 " + (index + 1) + " 个聊天 ID 无效");
        }
        if (!session.has("messages") || session.isNull("messages") || session.optJSONArray("messages") == null) {
            throw new BackupException("第 " + (index + 1) + " 个聊天缺少消息数组");
        }
        JSONArray messages = session.optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            throw new BackupException("第 " + (index + 1) + " 个聊天不包含消息");
        }
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) {
                throw new BackupException("第 " + (index + 1) + " 个聊天的第 " + (i + 1) + " 条消息损坏");
            }
            if (!message.has("role") || message.isNull("role") || !(message.opt("role") instanceof String)
                    || message.optString("role", "").trim().isEmpty()) {
                throw new BackupException("第 " + (index + 1) + " 个聊天的第 " + (i + 1) + " 条消息缺少角色");
            }
            if (!message.has("text") || message.isNull("text") || !(message.opt("text") instanceof String)) {
                throw new BackupException("第 " + (index + 1) + " 个聊天的第 " + (i + 1) + " 条消息缺少正文");
            }
        }
    }

    private static void validateSession(ChatStore.Session session, int index) throws BackupException {
        String id = session.id == null ? "" : session.id.trim();
        if (id.isEmpty() || id.length() > 128 || !id.matches("[A-Za-z0-9._-]+")) {
            throw new BackupException("第 " + (index + 1) + " 个聊天 ID 无效");
        }
        for (int i = 0; i < session.messages.length(); i++) {
            JSONObject message = session.messages.optJSONObject(i);
            if (message == null) {
                throw new BackupException("聊天“" + displayTitle(session) + "”的第 " + (i + 1) + " 条消息损坏");
            }
            try {
                message.put("text", sanitizeText(message.optString("text", "")));
                message.put("reasoning", sanitizeText(message.optString("reasoning", "")));
                JSONObject metadata = message.optJSONObject("metadata");
                if (metadata != null) {
                    metadata.remove("generated_office_files");
                }
            } catch (Exception error) {
                throw new BackupException("聊天“" + displayTitle(session) + "”的第 " + (i + 1) + " 条消息损坏", error);
            }
        }
        session.id = id;
        session.title = safe(session.title).trim();
        if (session.title.isEmpty()) {
            session.title = "新聊天";
        } else if (session.title.length() > 500) {
            session.title = session.title.substring(0, 500);
        }
        long now = System.currentTimeMillis();
        if (session.createdAt <= 0L) {
            session.createdAt = now;
        }
        if (session.updatedAt <= 0L) {
            session.updatedAt = session.createdAt;
        }
        session.pinnedAt = Math.max(0L, session.pinnedAt);
    }

    private static JSONObject backupSession(ChatStore.Session source) throws Exception {
        JSONObject session = new JSONObject();
        session.put("id", safe(source.id));
        session.put("title", safe(source.title));
        session.put("createdAt", Math.max(0L, source.createdAt));
        session.put("updatedAt", Math.max(0L, source.updatedAt));
        session.put("pinnedAt", Math.max(0L, source.pinnedAt));
        JSONArray messages = new JSONArray();
        if (source.messages != null) {
            for (int i = 0; i < source.messages.length(); i++) {
                JSONObject message = source.messages.optJSONObject(i);
                if (message != null) {
                    messages.put(backupMessage(message));
                }
            }
        }
        session.put("messages", messages);
        return session;
    }

    private static JSONObject backupMessage(JSONObject source) throws Exception {
        if (!source.has("role") || source.isNull("role") || !(source.opt("role") instanceof String)
                || !source.has("text") || source.isNull("text") || !(source.opt("text") instanceof String)) {
            throw new BackupException("聊天消息格式无效");
        }
        JSONObject message = new JSONObject();
        message.put("role", source.getString("role"));
        message.put("text", sanitizeText(source.getString("text")));
        if (source.has("reasoning") && !source.isNull("reasoning") && source.opt("reasoning") instanceof String) {
            message.put("reasoning", sanitizeText(source.getString("reasoning")));
        }
        if (source.has("sources") && source.optJSONArray("sources") != null) {
            message.put("sources", new JSONArray(source.getJSONArray("sources").toString()));
        }
        JSONObject metadata = source.optJSONObject("metadata");
        if (metadata != null) {
            JSONObject backupMetadata = branchMetadata(metadata);
            if (backupMetadata.length() > 0) {
                message.put("metadata", backupMetadata);
            }
        }
        if (source.has("elapsedMs")) {
            message.put("elapsedMs", Math.max(0L, source.optLong("elapsedMs", 0L)));
        }
        if (source.has("time")) {
            message.put("time", Math.max(0L, source.optLong("time", 0L)));
        }
        return message;
    }

    private static JSONObject branchMetadata(JSONObject source) throws Exception {
        JSONObject result = new JSONObject();
        for (String key : BRANCH_METADATA_KEYS) {
            if (source.has(key) && !source.isNull(key)) {
                result.put(key, source.get(key));
            }
        }
        return result;
    }

    private static String sanitizeText(String value) {
        String sanitized = value == null ? "" : value;
        sanitized = DATA_IMAGE_PATTERN.matcher(sanitized).replaceAll("[图片已省略]");
        sanitized = LOCAL_IMAGE_MARKDOWN_PATTERN.matcher(sanitized).replaceAll("[图片已省略]");
        return REMOTE_GENERATED_IMAGE_MARKDOWN_PATTERN.matcher(sanitized).replaceAll("[图片已省略]");
    }

    private static String displayTitle(ChatStore.Session session) {
        String title = safe(session.title).trim();
        return title.isEmpty() ? session.id : title;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class RestorePlan {
        final List<ChatStore.Session> sessions;
        final String currentSessionId;
        final int messageCount;
        final long exportedAt;
        final String appVersion;

        RestorePlan(
                List<ChatStore.Session> sessions,
                String currentSessionId,
                int messageCount,
                long exportedAt,
                String appVersion
        ) {
            this.sessions = sessions;
            this.currentSessionId = currentSessionId;
            this.messageCount = messageCount;
            this.exportedAt = exportedAt;
            this.appVersion = appVersion;
        }
    }

    static final class BackupException extends Exception {
        BackupException(String message) {
            super(message);
        }

        BackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
