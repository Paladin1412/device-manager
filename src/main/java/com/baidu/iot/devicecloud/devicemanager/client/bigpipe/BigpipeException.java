package com.baidu.iot.devicecloud.devicemanager.client.bigpipe;

import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * The exception for bigpipe.
 *
 * @author Shen Dayu (shendayu@baidu.com)
 **/
@Getter
@Component
public class BigpipeException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Whether to ignore exception when logging.
     */
    private final boolean ignorable;

    public BigpipeException() {
        super();
        this.ignorable = false;
    }

    public BigpipeException(boolean ignorable, String message, Throwable cause) {
        super(message, cause);
        this.ignorable = ignorable;
    }

    public BigpipeException(String message, Throwable cause) {
        super(message, cause);
        this.ignorable = false;
    }

    public BigpipeException(String message) {
        super(message);
        this.ignorable = false;
    }

    public BigpipeException(Throwable cause) {
        super(cause);
        this.ignorable = false;
    }
}
