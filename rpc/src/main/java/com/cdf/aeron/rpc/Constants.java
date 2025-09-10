package com.cdf.aeron.rpc;

import static com.cdf.aeron.common.constant.Constants.AERON_UDP_ENDPOINT;

/**
 * @author chendifan
 * @date 2024-09-01
 */
public class Constants {
    public static final int RPC_STREAM = 1;
    public static final String SERVER_INBOUND_URI = AERON_UDP_ENDPOINT + "127.0.0.1:2000";
    public static final String CLIENT_INBOUND_URI = AERON_UDP_ENDPOINT + "127.0.0.1:2001";
    public static final String AERON_DIR = "./aeron-dir";
}
