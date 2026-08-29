package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageDeltaPlannerTest {
    @Test
    public void unchangedMessagesProduceNoDatabaseWrites() throws Exception {
        JSONArray stored = messages("a", "b");
        JSONArray desired = new JSONArray(stored.toString());

        MessageDeltaPlanner.Plan plan = MessageDeltaPlanner.plan(stored, desired);

        assertTrue(plan.insertPositions.isEmpty());
        assertTrue(plan.updatePositions.isEmpty());
        assertEquals(-1, plan.deleteFromPosition);
    }

    @Test
    public void appendedMessageProducesOneInsertOnly() throws Exception {
        JSONArray stored = messages("a", "b");
        JSONArray desired = new JSONArray(stored.toString());
        desired.put(message("c", "n3"));

        MessageDeltaPlanner.Plan plan = MessageDeltaPlanner.plan(stored, desired);

        assertEquals(1, plan.insertPositions.size());
        assertEquals(Integer.valueOf(2), plan.insertPositions.get(0));
        assertTrue(plan.updatePositions.isEmpty());
        assertEquals(-1, plan.deleteFromPosition);
    }

    @Test
    public void changedBranchMetadataUpdatesOnlyItsMessage() throws Exception {
        JSONArray stored = messages("a", "b", "c");
        JSONArray desired = new JSONArray(stored.toString());
        desired.getJSONObject(0).getJSONObject("metadata").put("selectedChildNodeId", "n3");

        MessageDeltaPlanner.Plan plan = MessageDeltaPlanner.plan(stored, desired);

        assertEquals(1, plan.updatePositions.size());
        assertEquals(Integer.valueOf(0), plan.updatePositions.get(0));
        assertTrue(plan.insertPositions.isEmpty());
    }

    @Test
    public void shorterConversationDeletesOnlyTheStaleTail() throws Exception {
        JSONArray stored = messages("a", "b", "c");
        JSONArray desired = messages("a", "b");

        MessageDeltaPlanner.Plan plan = MessageDeltaPlanner.plan(stored, desired);

        assertEquals(2, plan.deleteFromPosition);
        assertTrue(plan.insertPositions.isEmpty());
        assertTrue(plan.updatePositions.isEmpty());
    }

    @Test
    public void canonicalComparisonIgnoresJsonObjectKeyOrder() throws Exception {
        JSONObject leftMetadata = new JSONObject();
        leftMetadata.put("nodeId", "n1");
        leftMetadata.put("parentNodeId", "");
        JSONObject rightMetadata = new JSONObject();
        rightMetadata.put("parentNodeId", "");
        rightMetadata.put("nodeId", "n1");

        JSONObject left = message("same", "n1");
        left.put("metadata", leftMetadata);
        JSONObject right = message("same", "n1");
        right.put("metadata", rightMetadata);

        assertFalse(MessageDeltaPlanner.messagesDiffer(left, right));
    }

    private JSONArray messages(String... values) throws Exception {
        JSONArray messages = new JSONArray();
        for (int i = 0; i < values.length; i++) {
            messages.put(message(values[i], "n" + (i + 1)));
        }
        return messages;
    }

    private JSONObject message(String text, String nodeId) throws Exception {
        JSONObject metadata = new JSONObject();
        metadata.put("nodeId", nodeId);
        metadata.put("parentNodeId", "");
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("text", text);
        message.put("reasoning", "");
        message.put("metadata", metadata);
        message.put("time", 100L);
        return message;
    }
}
