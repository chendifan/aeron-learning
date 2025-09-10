package com.cdf.aeron.rpc.client;

import com.cdf.aeron.common.help.ExitSignalListener;
import com.cdf.aeron.rpc.sbe.HeaderDecoder;
import com.cdf.aeron.rpc.sbe.MyResponseDecoder;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;

/**
 * 模拟 RPC client，从响应 buffer 中识别完整的请求并进行处理
 *
 * @author chendifan
 * @date 2024-09-01
 */
@Slf4j
public class ClientAdapter implements FragmentHandler {
    private final HeaderDecoder headerDecoder;
    private final MyResponseDecoder responseDecoder;

    public ClientAdapter() {
        this.headerDecoder = new HeaderDecoder();
        this.responseDecoder = new MyResponseDecoder();
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        headerDecoder.wrap(buffer, offset);
        if (headerDecoder.templateId() == MyResponseDecoder.TEMPLATE_ID) {
            responseDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
            if (log.isInfoEnabled()) {
                log.info("response received, id: {}, res: {}", responseDecoder.id(), responseDecoder.res());
            }
            ExitSignalListener.signalShutdown();
        } else {
            log.warn("unknown message, templateId: {}", headerDecoder.templateId());
        }
    }
}
