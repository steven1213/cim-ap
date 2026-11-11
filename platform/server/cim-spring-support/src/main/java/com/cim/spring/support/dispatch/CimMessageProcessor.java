package com.cim.spring.support.dispatch;

/**
 * 消息处理器：一种请求 DTO 对应一个实现。
 *
 * <p><b>只返回业务数据</b>——信封（{@code Result}）由 {@link CimMessageDispatcher} 统一包装。
 * 这样做的理由是「契约不能被实现方漏掉」：若接口签名要求返回 {@code Result}，任何一处
 * {@code return someRawObject;} 都会打破前后端约定，而编译器不会拦。</p>
 *
 * <p>失败请抛 {@code BizException}（业务可读原因）或 {@code SystemException}（系统故障），
 * 由全局异常处理器生成带业务码与 traceId 的标准失败信封。</p>
 *
 * <p><b>事务</b>：处理器自行声明 {@code @Transactional}。分发入口刻意**不**加事务——
 * 同一入口会承载查询、写操作与外部调用，一刀切加事务会把只读消息也拖进写事务。</p>
 *
 * @param <R> 请求类型（{@link CimMessage} 标注的那个 DTO）
 */
public interface CimMessageProcessor<R> {

    /**
     * 处理一条消息。
     *
     * @param request 已反序列化的请求对象，非 {@code null}（空报文会给出空实例）
     * @return 业务数据；可为 {@code null}
     */
    Object process(R request);
}
