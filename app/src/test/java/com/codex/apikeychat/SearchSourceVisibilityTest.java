package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchSourceVisibilityTest {
    @Test
    public void hidesSourcesWhenAnswerDoesNotCiteThem() throws Exception {
        JSONArray sources = new JSONArray()
                .put(source("A", "https://example.com/a"))
                .put(diagnostic());

        JSONArray visible = SearchSourceVisibility.visibleSourcesForAnswer("画一个搜索流程图", sources);

        assertEquals(0, visible.length());
    }

    @Test
    public void keepsOnlyCitedSourcesAndDiagnostics() throws Exception {
        JSONArray sources = new JSONArray()
                .put(source("A", "https://example.com/a"))
                .put(source("B", "https://example.com/b"))
                .put(diagnostic());

        JSONArray visible = SearchSourceVisibility.visibleSourcesForAnswer("最近进展包括 A [1]。", sources);

        assertEquals(2, visible.length());
        assertEquals("https://example.com/a", visible.getJSONObject(0).getString("url"));
        assertEquals("diagnostic", visible.getJSONObject(1).getString("kind"));
    }

    @Test
    public void detectsNumberedCitations() {
        assertTrue(SearchSourceVisibility.answerUsesSourceCitations("结论来自这里 [12]。"));
        assertTrue(SearchSourceVisibility.answerUsesSourceCitations("DeepSeek-V3[1] 发布。"));
        assertFalse(SearchSourceVisibility.answerUsesSourceCitations("数组是 [a, b]，不是来源。"));
    }

    private static JSONObject source(String title, String url) throws Exception {
        JSONObject source = new JSONObject();
        source.put("kind", "source");
        source.put("title", title);
        source.put("url", url);
        return source;
    }

    private static JSONObject diagnostic() throws Exception {
        JSONObject diagnostic = new JSONObject();
        diagnostic.put("kind", "diagnostic");
        diagnostic.put("title", "搜索诊断");
        return diagnostic;
    }
}
