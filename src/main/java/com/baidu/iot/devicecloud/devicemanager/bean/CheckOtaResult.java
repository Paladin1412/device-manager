package com.baidu.iot.devicecloud.devicemanager.bean;

import lombok.Data;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/6/12.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
public class CheckOtaResult {
    private Long otaTaskId;

    private String deviceUuid;

    private Long osVersionId;

    private Long packageId;

    private Long strategyId;

    private OtaStrategyType type = OtaStrategyType.NULL;

    private String newOsVersion;

    private String originalOsVersion;

    private State state = State.NOT_UPDATABLE;

    public enum State {
        NOT_UPDATABLE, UPDATABLE, UPDATING, UPDATE_SUCCESS, UPDATE_FAIL
    }
}
