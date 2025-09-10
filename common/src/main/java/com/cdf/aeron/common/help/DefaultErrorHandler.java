package com.cdf.aeron.common.help;

import lombok.extern.slf4j.Slf4j;
import org.agrona.ErrorHandler;

/**
 * @author chendifan
 * @date 2024-09-01
 */
@Slf4j
public class DefaultErrorHandler implements ErrorHandler {
    public static final DefaultErrorHandler INSTANCE = new DefaultErrorHandler();

    private DefaultErrorHandler() {
    }

    @Override
    public void onError(Throwable t) {
        log.error("error occurred", t);
    }
}
