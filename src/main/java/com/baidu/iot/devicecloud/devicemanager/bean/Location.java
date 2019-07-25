package com.baidu.iot.devicecloud.devicemanager.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/7/24.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Location {
    private Float longitude;
    private Float latitude;
    private String geoCoordinateSystem;
}
