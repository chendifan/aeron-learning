package com.cdf.aeron.rpc.server;

import com.cdf.aeron.rpc.Constants;
import io.aeron.Aeron;
import io.aeron.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;

/**
 * 模拟 RPC server 不断处理入站字节（RPC Client 发的请求），然后发送响应
 *
 * @author chendifan
 * @date 2024-09-01
 */
@Slf4j
public class ServerAgent implements Agent {
    private final Aeron aeron;
    private final ServerAdapter serverAdapter;

    private Subscription subscription;

    public ServerAgent(Aeron aeron) {
        this.aeron = aeron;
        this.serverAdapter = new ServerAdapter(aeron);
    }

    @Override
    public void onStart() {
        // 增加一个 subscription，也就是 inbound channel，监听 2000 端口 UDP 协议，当有 Image 可用时获取一个回调
        subscription = aeron.addSubscription(Constants.SERVER_INBOUND_URI, Constants.RPC_STREAM);
        log.info("inbound connected, uri: {}", subscription.channel());
    }

    @Override
    public int doWork() {
        return subscription.poll(serverAdapter, 1);
    }

    @Override
    public void onClose() {
        serverAdapter.onClose();
    }

    @Override
    public String roleName() {
        return "rpc-server";
    }
}