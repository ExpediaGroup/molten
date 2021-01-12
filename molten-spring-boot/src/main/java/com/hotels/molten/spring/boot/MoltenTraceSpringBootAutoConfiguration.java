package com.hotels.molten.spring.boot;

import javax.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.autoconfig.instrument.reactor.TraceReactorAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import com.hotels.molten.trace.MoltenTrace;

@Configuration
@ConditionalOnProperty(value = "spring.sleuth.reactor.enabled", matchIfMissing = true)
@AutoConfigureAfter(value = TraceReactorAutoConfiguration.class)
public class MoltenTraceSpringBootAutoConfiguration {

    @PostConstruct
    public void initTracing() {
        MoltenTrace.initialize(false);
    }
}
