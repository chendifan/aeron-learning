package com.cdf.aeron.common.extend;

import com.cdf.aeron.common.help.Ordered;

/**
 * @author chendifan
 * @date 2025-09-10
 */
public interface OrderedNamedRunnable extends Runnable, Ordered {
    String getName();
}
