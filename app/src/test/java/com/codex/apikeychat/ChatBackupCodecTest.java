package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ChatBackupCodecTest {
    @Test
    public void backupRoundTripPreservesSessionsMessagesAndBranchMetadata() throws Exception {
        ChatStore.Session first = session("s100", "分支测试", 100L);
        first.messages.put(message("user", "问题", "n1", "", "n2"));
        first.messages.put(message("assistant", "回答 A", "n2", "n1", ""));
        first.messages.put(message("assistant", "回答 B", "n3", "n1", ""));

        ChatStore.Session second = session("s200", "普通聊天", 200L);
        second.messages.put(message("user", "你好", "n4", "", "n5"));
        second.messages.put(message("assistant", "你好", "n5", "n4", ""));

        String encoded = ChatBackupCodec.encode(
                Arrays.asList(first, second),
                first.id,
                123456L,
                "1.10.0"
        );
        ChatBackupCodec.RestorePlan restored = ChatBackupCodec.decode(encoded);

        assertEquals(2, restored.sessions.size());
        assertEquals(5, restored.messageCount);
        assertEquals("s100", restored.currentSessionId);
        assertEquals("回答 B", restored.sessions.get(0).messages.getJSONObject(2).getString("text"));
        assertEquals(
                "n2",
                restored.sessions.get(0).messages.getJSONObject(0)
                        .getJSONObject("metadata")
                        .getString("selectedChildNodeId")
        );
        assertFalse(encoded.contains("api_key"));
        assertFalse(encoded.contains("responseId"));
        assertFalse(encoded.contains("lastModel"));
        assertFalse(encoded.contains("apiMode"));
        assertFalse(encoded.contains("transcript"));
    }

    @Test
    public void invalidFormatAndDuplicateSessionIdsAreRejected() throws Exception {
        try {
            ChatBackupCodec.decode("{\"format\":\"other\",\"schemaVersion\":1,\"sessions\":[]}");
            fail("invalid format should fail");
        } catch (ChatBackupCodec.BackupException expected) {
            assertTrue(expected.getMessage().contains("格式"));
        }

        ChatStore.Session session = session("same", "重复", 100L);
        session.messages.put(message("user", "内容", "n1", "", ""));
        String encoded = ChatBackupCodec.encode(
                Arrays.asList(session, session),
                "same",
                123456L,
                "1.10.0"
        );
        try {
            ChatBackupCodec.decode(encoded);
            fail("duplicate ids should fail");
        } catch (ChatBackupCodec.BackupException expected) {
            assertTrue(expected.getMessage().contains("重复"));
        }
    }

    @Test
    public void currentSessionFallsBackToFirstRestoredSession() throws Exception {
        ChatStore.Session session = session("s300", "唯一聊天", 300L);
        session.messages.put(message("user", "内容", "n1", "", ""));

        String encoded = ChatBackupCodec.encode(
                Arrays.asList(session),
                "missing",
                123456L,
                "1.10.0"
        );
        ChatBackupCodec.RestorePlan restored = ChatBackupCodec.decode(encoded);

        assertEquals("s300", restored.currentSessionId);
    }

    @Test
    public void backupOmitsGeneratedImagesAndOfficeFileReferences() throws Exception {
        ChatStore.Session session = session("s400", "附件聊天", 400L);
        JSONObject message = message("assistant", "图片 ![生成图片](file:///data/user/0/app/files/generated_images/image.png) data:image/png;base64,AAAA", "n1", "", "");
        JSONObject metadata = message.getJSONObject("metadata");
        JSONArray officeFiles = new JSONArray();
        officeFiles.put(new JSONObject().put("name", "report.docx").put("privatePath", "/data/user/0/app/files/generated-office/report.docx"));
        metadata.put("generated_office_files", officeFiles);
        session.messages.put(message);

        String encoded = ChatBackupCodec.encode(
                Arrays.asList(session),
                session.id,
                123456L,
                "1.10.0"
        );

        assertFalse(encoded.contains("data:image"));
        assertFalse(encoded.contains("generated_images"));
        assertFalse(encoded.contains("generated_office_files"));
        assertFalse(encoded.contains("report.docx"));
        ChatBackupCodec.RestorePlan restored = ChatBackupCodec.decode(encoded);
        assertTrue(restored.sessions.get(0).messages.getJSONObject(0).getString("text").contains("图片已省略"));
    }

    private ChatStore.Session session(String id, String title, long time) {
        ChatStore.Session session = new ChatStore.Session();
        session.id = id;
        session.title = title;
        session.createdAt = time;
        session.updatedAt = time;
        session.messages = new JSONArray();
        return session;
    }

    private JSONObject message(
            String role,
            String text,
            String nodeId,
            String parentNodeId,
            String selectedChildNodeId
    ) throws Exception {
        JSONObject metadata = new JSONObject();
        metadata.put("nodeId", nodeId);
        metadata.put("parentNodeId", parentNodeId);
        if (!selectedChildNodeId.isEmpty()) {
            metadata.put("selectedChildNodeId", selectedChildNodeId);
        }
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("text", text);
        message.put("reasoning", "");
        message.put("metadata", metadata);
        message.put("time", 100L);
        return message;
    }
}
