package com.cdf.aeron.rpc.client;

import com.cdf.aeron.common.util.ProcessUtils;
import com.cdf.aeron.rpc.Constants;
import com.cdf.aeron.rpc.sbe.HeaderEncoder;
import com.cdf.aeron.rpc.sbe.MyConnectEncoder;
import com.cdf.aeron.rpc.sbe.MyRequestEncoder;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.agrona.CloseHelper.quietClose;

/**
 * 状态机，模拟 RPC client 不断处理入站字节（RPC server 给的响应），然后发送请求
 *
 * @author chendifan
 * @date 2024-09-01
 */
@Slf4j
public class ClientAgent implements Agent {
    private final Aeron aeron;
    private final IdleStrategy idleStrategy;
    // 虽然 decoder 带状态，但这里是模拟 RPC client，只有一个线程顺序处理所有事件
    private final ClientAdapter clientAdapter;
    private final HeaderEncoder headerEncoder;
    private final MyConnectEncoder connectEncoder;
    private final MyRequestEncoder requestEncoder;
    private final ExpandableDirectByteBuffer buffer;

    private State state;
    private ExclusivePublication publication;
    private Subscription subscription;
    private long id;

    public ClientAgent(Aeron aeron) {
        this.aeron = aeron;
        this.idleStrategy = new BackoffIdleStrategy();
        this.clientAdapter = new ClientAdapter();
        this.headerEncoder = new HeaderEncoder();
        this.connectEncoder = new MyConnectEncoder();
        this.requestEncoder = new MyRequestEncoder();
        this.buffer = new ExpandableDirectByteBuffer(250);
    }

    @Override
    public void onStart() {
        state = State.AWAITING_OUTBOUND_CONNECT;
        // 声明一个 publication，用于发送请求到 server，对应 outbound channel
        publication = aeron.addExclusivePublication(Constants.SERVER_INBOUND_URI, Constants.RPC_STREAM);
        // onConnect 会使用 client 的 uri 告诉 server 响应应当发送到哪个 channel，这里建立对应的 subscription，对应 inbound channel
        subscription = aeron.addSubscription(Constants.CLIENT_INBOUND_URI, Constants.RPC_STREAM);
        log.info("rpc client started");
    }

    @Override
    public int doWork() {
        switch (state) {
            // 写请求的 publication，相当于等待与 Server 建立请求 channel
            case AWAITING_OUTBOUND_CONNECT -> {
                awaitConnected();
                state = State.CONNECTED;
            }
            // 请求 channel 建连完成，发送 connect 消息告知 server 响应 channel
            case CONNECTED -> {
                sendConnectRequest();
                state = State.AWAITING_INBOUND_CONNECT;
            }
            // 等待与 server 建立响应 channel
            case AWAITING_INBOUND_CONNECT -> {
                awaitSubscriptionConnected();
                state = State.READY;
            }
            // 响应 channel 建连完成，双工通信条件具备，开始发请求消息
            case READY -> {
                sendMessage();
                state = State.AWAITING_RESULT;
            }
            // 等响应，这里直接模拟请求响应模型，阻塞等待
            case AWAITING_RESULT -> {
                int workCount = 0;
                while (workCount <= 0) {
                    workCount = subscription.poll(clientAdapter, 1);
                    idleStrategy.idle(workCount);
                    if (state == State.CLOSED) {
                        idleStrategy.reset();
                        return workCount;
                    }
                }
                idleStrategy.reset();
                state = State.READY;
            }
            case CLOSED -> {
                return 0;
            }
            default -> {
            }
        }
        return 0;
    }

    @Override
    public void onClose() {
        state = State.CLOSED;
        quietClose(publication);
        quietClose(subscription);
    }

    @Override
    public String roleName() {
        return "rpc-client";
    }

    private void sendMessage() {
        // 5s 一次请求
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5L));
        String req = "client time: " + System.currentTimeMillis();
        // 把 header 写入 buffer
        requestEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        requestEncoder.id(id);
        requestEncoder.req(req);
        send(buffer, headerEncoder.encodedLength() + requestEncoder.encodedLength());
        log.info("request sent, id: {}, req: {}", id, req);
        id++;
    }

    private void sendConnectRequest() {
        connectEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        // subscription 对应的 inbound channel
        connectEncoder.channel(Constants.CLIENT_INBOUND_URI);
        connectEncoder.streamId(1);
        send(buffer, headerEncoder.encodedLength() + connectEncoder.encodedLength());
    }

    private void awaitSubscriptionConnected() {
        while (state != State.CLOSED && !subscription.isConnected()) {
            aeron.context().idleStrategy().idle();
        }
        log.info("inbound connected, uri: {}", subscription.channel());
        ProcessUtils.lsofUdp();
        ProcessUtils.tree(new File(Constants.AERON_DIR));
    }

    private void awaitConnected() {
        while (state != State.CLOSED && !publication.isConnected()) {
            aeron.context().idleStrategy().idle();
        }
        log.info("outbound connected, uri: {}", publication.channel());
    }

    private void send(DirectBuffer buffer, int length) {
        while (state != State.CLOSED) {
            long streamPos = publication.offer(buffer, 0, length);
            if (streamPos >= 0) {
                break;
            }
            idleStrategy.idle();
        }
        idleStrategy.reset();
    }

    enum State {
        AWAITING_OUTBOUND_CONNECT,
        CONNECTED,
        AWAITING_INBOUND_CONNECT,
        READY,
        AWAITING_RESULT,
        CLOSED
    }
}
