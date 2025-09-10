package com.cdf.aeron.rpc.server;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.cdf.aeron.common.help.DefaultErrorHandler;
import com.cdf.aeron.common.util.ProcessUtils;
import com.cdf.aeron.rpc.Constants;
import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

/**
 * @author chendifan
 * @date 2024-09-01
 */
@Slf4j
public class RpcServer {
    public static void main(String[] args) {
        ShutdownSignalBarrier ssb = new ShutdownSignalBarrier();
        ProcessUtils.registerShutdownHook(ssb::signal);

        // 创建一个 MediaDriver
        MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
                .aeronDirectoryName(Constants.AERON_DIR + "/aeron-rpc-server")
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(false)
                // 提供专用线程的线程模型，Sender、Receiver、Conductor 各一个线程
                .threadingMode(ThreadingMode.DEDICATED);
        // embedded
        MediaDriver mediaDriver = MediaDriver.launch(mediaDriverCtx);

        // 创建一个 Aeron 实例，连接到 MediaDriver 以进行 pub
        Aeron.Context aeronCtx = new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName());
        Aeron aeron = Aeron.connect(aeronCtx);

        /*
          agrona 编程模型，创建一个 Agent，包装成 AgentRunner 这个 Runnable，然后不断地运行 doWork 方法，
          这里是模拟 RPC server 单独一个线程（现实可能不止一个，取决于你的 RPC 线程模型）处理请求、接收响应
         */
        ServerAgent serverAgent = new ServerAgent(aeron);
        AgentRunner agentRunner = new AgentRunner(new BackoffIdleStrategy(), DefaultErrorHandler.INSTANCE, null, serverAgent);
        AgentRunner.startOnThread(agentRunner, ThreadFactoryBuilder.create().setDaemon(false).setNamePrefix("RPC-Server-").build());

        // 等待进程退出
        ssb.await();
        log.info("process exiting...");
        /*
          1. 先关 Agent，也就是 publication & subscription，释放 log buffer 文件
          2. 再关 Aeron，也就是 Conductor
          3. 最后关 MediaDriver (包含 MediaDriver 的 Conductor)
         */
        CloseHelper.quietCloseAll(agentRunner, aeron, mediaDriver);
    }
}