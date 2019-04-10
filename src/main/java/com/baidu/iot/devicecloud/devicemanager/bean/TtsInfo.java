package com.baidu.iot.devicecloud.devicemanager.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * Created by Yao Gang (yaogang@baidu.com) on 2019/3/26.
 * @see <a href="http://agroup.baidu.com/share/md/410c856160fc41b5a7ac0e2afe2c3ca0">TTS request parameters</a>
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class TtsInfo {
    @JsonProperty("pre_tts")
    @JsonIgnore
    private boolean preTts = false;

    private String text;
    private String volume;
    private String speed;
    private String xml;

    @JsonProperty("pid")
    private String productId;

    private String key;
    private String speaker;
    private String pitch;
    private String aue;
    private String rate;

    @JsonIgnore
    @JsonProperty("tts_optional")
    private String options;

    @JsonProperty("content_id")
    private String contentId;

    @JsonIgnore
    private String cuid;
}
