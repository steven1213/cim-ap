package com.cim.mq;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 真集成测试的 broker 可达性探测。
 *
 * <p>本包下的 {@code RabbitMqIntegrationTest} / {@code PulsarMqIntegrationTest} 连的是
 * Docker 里的真实 broker。开发者本机没起 Docker 时，这些测试若照常执行会在 Spring 上下文
 * 阶段就因 {@code Connection refused} 失败，把整个模块的构建判红——而这不是代码缺陷。
 * 故用 {@code @EnabledIf} 在**加载上下文之前**探测端口：可达才跑，不可达标记为 skipped。</p>
 *
 * <p>与 notify-starter 的 {@code NotifyRedisFanoutTest} 用 {@code assumeTrue} 同一套思路，
 * 区别是这里必须更早判定（bean 创建阶段就会连 broker，{@code assumeTrue} 来不及）。</p>
 */
public final class BrokerProbe {

    private BrokerProbe() {
    }

    /** RabbitMQ 默认端口。 */
    public static boolean rabbit() {
        return reachable(5672);
    }

    /** Pulsar standalone 默认端口。 */
    public static boolean pulsar() {
        return reachable(6650);
    }

    private static boolean reachable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
