package com.cdf.aeron.common.help;

/**
 * @author chendifan
 * @date 2025-09-01
 */
public interface Ordered {
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    int getOrder();
}
