package com.baidu.iot.devicecloud.devicemanager.client.http.dlpclient.builder;

import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DIRECTIVE_NAMESPACE_PREFIX;
import static com.baidu.iot.devicecloud.devicemanager.constant.DCSProxyConstant.DLP_AUDIO_PLAYER_STATUS_NAME;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/11.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Slf4j
@Data
public class DlpStatusBuilder {
    private static Map<String, String> playerActivity = new HashMap<>();

    static {
        playerActivity.put("PlaybackStarted", "PLAYING");
        playerActivity.put("PlaybackNearlyFinished", "PLAYING");
        playerActivity.put("ProgressReportDelayElapsed", "PLAYING");
        playerActivity.put("ProgressReportIntervalElapsed", "PLAYING");
        playerActivity.put("PlaybackStutterFinished", "PLAYING");
        playerActivity.put("PlaybackResumed", "PLAYING");
        playerActivity.put("StreamMetadataExtracted", "PLAYING");

        playerActivity.put("PlaybackFailed", "STOPPED");
        playerActivity.put("PlaybackStopped", "STOPPED");
        playerActivity.put("PlaybackQueueCleared", "STOPPED");

        playerActivity.put("PlaybackPaused", "PAUSED");

        playerActivity.put("PlaybackStutterStarted", "BUFFER_UNDERRUN");

        playerActivity.put("PlaybackFinished", "FINISHED");

    }

    private ObjectNode data = JsonUtil.createObjectNode();
    private ObjectNode header = JsonUtil.createObjectNode();
    private ObjectNode toClient = JsonUtil.createObjectNode();

    public DlpStatusBuilder() {
        data.set("to_client", toClient);
        header.set("name", TextNode.valueOf(DLP_AUDIO_PLAYER_STATUS_NAME));
        header.set("messageId", TextNode.valueOf(UUID.randomUUID().toString()));
        toClient.set("header", header);
    }

    public DlpStatusBuilder namespace(String namespace) {
        header.set("namespace",
                TextNode.valueOf(namespace.replace(DIRECTIVE_NAMESPACE_PREFIX, "dlp")));
        return this;
    }

    public DlpStatusBuilder payload(JsonNode payload) {
        toClient.set("payload", payload);
        return this;
    }

    private DlpStatusBuilder playerActivity(String activity) {
        if (activity != null && !toClient.path("payload").isNull()) {
            ObjectNode asObject = (ObjectNode) toClient.path("payload");
            asObject.set("playerActivity", TextNode.valueOf(activity));
        }
        return this;
    }

    public DlpStatusBuilder supplyStatus(JsonNode header) {
        return this.namespace(header.path("namespace").asText())
            .playerActivity(playerActivity.get(header.path("name").asText()));
    }
}
