package com.cdf.aeron.rpc.server;

import com.cdf.aeron.common.util.ProcessUtils;
import com.cdf.aeron.rpc.Constants;
import com.cdf.aeron.rpc.sbe.*;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import java.io.File;

import static org.agrona.CloseHelper.quietClose;

/**
 * 模拟 RPC server，从请求 buffer 中识别完整的请求并进行处理
 *
 * @author chendifan
 * @date 2024-09-01
 */
@Slf4j
public class ServerAdapter implements FragmentHandler {

    private final Aeron aeron;
    private final IdleStrategy idleStrategy;
    // 虽然 decoder 带状态，但这里是模拟 RPC server，只有一个线程
    private final HeaderEncoder headerEncoder;
    private final HeaderDecoder headerDecoder;
    private final MyConnectDecoder connectDecoder;
    private final MyRequestDecoder requestDecoder;
    private final MyResponseEncoder responseEncoder;
    private final ExpandableDirectByteBuffer reusedBuffer;

    // 正常来说，有多个 Client，那应该是多个 Publication
    private Publication publication;
    private volatile boolean running = true;

    public ServerAdapter(Aeron aeron) {
        this.aeron = aeron;
        this.idleStrategy = new BackoffIdleStrategy();
        this.headerDecoder = new HeaderDecoder();
        this.headerEncoder = new HeaderEncoder();
        this.connectDecoder = new MyConnectDecoder();
        this.requestDecoder = new MyRequestDecoder();
        this.responseEncoder = new MyResponseEncoder();
        this.reusedBuffer = new ExpandableDirectByteBuffer(512);
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        headerDecoder.wrap(buffer, offset);
        int headerLength = headerDecoder.encodedLength();
        int payloadLength = headerDecoder.blockLength();
        int version = headerDecoder.version();

        switch (headerDecoder.templateId()) {
            case MyConnectDecoder.TEMPLATE_ID:
                onConnect(buffer, offset, headerLength, payloadLength, version);
                break;
            case MyRequestDecoder.TEMPLATE_ID:
                onRequest(buffer, offset, headerLength, payloadLength, version);
                break;
            default:
                break;
        }
    }

    private void onConnect(DirectBuffer buffer, int offset, int headerLength, int payloadLength, int version) {
        // 解码 payload
        connectDecoder.wrap(buffer, offset + headerLength, payloadLength, version);
        // Client 声明的用于接收响应的 channel 的 media 地址
        String channel = connectDecoder.channel();
        // streamId 在 channel 中唯一
        int streamId = connectDecoder.streamId();
        // 单线程 pub
        publication = aeron.addExclusivePublication(channel, streamId);
        // session 是 MediaDriver 的所有 Publication 中唯一的
        // int sessionId = publication.sessionId();
        // 等待 Client 创建 subscription 连上 server
        while (running && !publication.isConnected()) {
            idleStrategy.idle();
        }
        idleStrategy.reset();
        log.info("outbound connected, uri: {}", channel);
        ProcessUtils.lsofUdp();
        ProcessUtils.tree(new File(Constants.AERON_DIR));
    }

    private void onRequest(DirectBuffer buffer, int offset, int headerLength, int payloadLength, int version) {
        requestDecoder.wrap(buffer, offset + headerLength, payloadLength, version);
        long id = requestDecoder.id();
        String req = requestDecoder.req();

        log.info("request received, id: {}, req: {}", id, req);
        String res = "server time " + System.currentTimeMillis();
        responseEncoder.wrapAndApplyHeader(reusedBuffer, 0, headerEncoder);
        responseEncoder.id(id);
        responseEncoder.res(res);

        while (running) {
            long streamPos = publication.offer(reusedBuffer, 0, headerEncoder.encodedLength() +
                    responseEncoder.encodedLength());
            if (streamPos >= 0) {
                log.info("response sent, id: {}, res: {}", id, res);
                break;
            }
            idleStrategy.idle();
        }
        idleStrategy.reset();
    }

    public void onClose() {
        running = false;
        quietClose(publication);
    }
}
