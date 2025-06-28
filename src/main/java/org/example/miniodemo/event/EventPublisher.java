package org.example.miniodemo.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 应用事件发布器。
 * <p>
 * 对Spring的 {@link ApplicationEventPublisher} 进行了简单的封装，
 * 以便在应用中更方便地以依赖注入的方式发布事件。
 */
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 发布一个应用事件。
     *
     * @param event 要发布的事件对象。
     */
    public void publish(Object event) {
        applicationEventPublisher.publishEvent(event);
    }
} 