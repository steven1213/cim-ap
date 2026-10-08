package com.cim.mq.listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.cim.mq.core.Acknowledgment;
import com.cim.mq.core.ConsumerOptions;
import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MessageHandler;
import com.cim.mq.core.MqTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

/**
 * 扫描 {@link MqListener} 标注方法并注册为消费者（容器刷新后统一注册，确保 MqTemplate 已就绪）。
 */
public class MqListenerPostProcessor implements BeanPostProcessor, SmartInitializingSingleton, BeanFactoryAware {

    private final MqTemplate mqTemplate;
    private BeanFactory beanFactory;
    private final List<Registration> pending = new ArrayList<>();

    public MqListenerPostProcessor(MqTemplate mqTemplate) {
        this.mqTemplate = mqTemplate;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> target = AopUtils.getTargetClass(bean);
        ReflectionUtils.doWithMethods(target, method -> {
            MqListener ann = AnnotatedElementUtils.findMergedAnnotation(method, MqListener.class);
            if (ann == null) {
                return;
            }
            validate(method);
            pending.add(new Registration(beanName, bean, method, ann));
        });
        return bean;
    }

    private void validate(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        if (pts.length == 1 && pts[0].isAssignableFrom(IntegrationEvent.class)) {
            return;
        }
        if (pts.length == 2 && pts[0].isAssignableFrom(IntegrationEvent.class)
                && pts[1].isAssignableFrom(Acknowledgment.class)) {
            return;
        }
        throw new IllegalStateException("@MqListener 方法签名必须为 (IntegrationEvent) 或 (IntegrationEvent, Acknowledgment): "
                + method);
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (Registration r : pending) {
            MessageHandler handler = (event, ack) -> invoke(r, event, ack);
            ConsumerOptions options = new ConsumerOptions(r.ann.topic(), r.ann.group())
                    .concurrency(r.ann.concurrency())
                    .maxAttempts(r.ann.maxAttempts());
            mqTemplate.registerListener(r.ann.topic(), r.ann.group(), handler, options);
        }
        pending.clear();
    }

    private void invoke(Registration r, IntegrationEvent event, Acknowledgment ack) {
        try {
            Class<?>[] pts = r.method.getParameterTypes();
            if (pts.length == 2) {
                r.method.invoke(r.bean, event, ack);
            } else {
                r.method.invoke(r.bean, event);
            }
        } catch (Exception e) {
            throw new com.cim.mq.core.exception.MqConsumeException(
                    "调用 @MqListener 方法失败: " + r.method, e instanceof RuntimeException ? e : e.getCause());
        }
    }

    private static final class Registration {
        final String beanName;
        final Object bean;
        final Method method;
        final MqListener ann;

        Registration(String beanName, Object bean, Method method, MqListener ann) {
            this.beanName = beanName;
            this.bean = bean;
            this.method = method;
            this.ann = ann;
        }
    }
}
