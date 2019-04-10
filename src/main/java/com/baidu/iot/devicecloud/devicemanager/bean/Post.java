package com.baidu.iot.devicecloud.devicemanager.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Created by Yao Gang (yaogang@baidu.com) on 2019/2/15.
 *
 * @author <a href="mailto:yaogang AT baidu DOT com">Yao Gang</a>
 */
@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Post {
    private Long id;
    private String title;
    private String content;
}
