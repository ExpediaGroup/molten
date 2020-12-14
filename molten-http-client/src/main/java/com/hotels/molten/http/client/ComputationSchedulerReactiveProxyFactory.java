/*
 * Copyright (c) 2020 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hotels.molten.http.client;

import java.lang.reflect.Method;
import javax.annotation.Nonnull;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Adds parallel scheduler publishing to the returned reactive results.
 */
@Slf4j
public class ComputationSchedulerReactiveProxyFactory extends AbstractReactiveProxyFactory {

    @Override
    protected <API> Mono<?> handleMonoInvocationSafely(@Nonnull Class<API> type, @Nonnull API service, Method method, Object[] args) {
        return safeInvokeMono(invokeMethod(service, method, args), type, service.getClass()).publishOn(Schedulers.parallel());
    }
}
