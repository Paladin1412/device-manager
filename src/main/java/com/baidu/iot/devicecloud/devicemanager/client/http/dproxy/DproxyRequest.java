package com.baidu.iot.devicecloud.devicemanager.client.http.dproxy;

import com.baidu.iot.devicecloud.devicemanager.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pansongsong02 on 2017/11/18.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DproxyRequest {
    private String cmd;
    private List args;

    public DproxyRequest(String cmd, Object... values) {
        this.cmd = cmd;
        List list = new ArrayList();
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof List) {
                List temp = (List) values[i];
                for (int j = 0; j < temp.size(); j++) {
                    mergeArgs(list, temp.get(j));
                }
            } else {
                mergeArgs(list, values[i]);
            }
        }
        this.args = list;
    }

    private static void mergeArgs(List list, Object value) {
        if (value instanceof String) {
            list.add(value);
        } else {
            list.add(JsonUtil.serialize(value));
        }
    }
}
