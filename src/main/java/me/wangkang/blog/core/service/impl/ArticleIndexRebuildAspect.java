/*
 * Copyright 2017 wangkang.me
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
package me.wangkang.blog.core.service.impl;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import me.wangkang.blog.core.evt.ArticleIndexRebuildEvent;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.lock.LockException;
import me.wangkang.blog.core.security.AuthencationException;
import me.wangkang.blog.util.ExceptionUtils;

/**
 * 当回滚时用来重建构建文章索引，只有处于事务中时才会生效
 * <p>
 * <b>{@code LogicException},{@code AuthencationException}以及{@code LockException}等异常引起的回滚不会被重新建立索引，因此索引的变更之后不应该再抛出上述异常</b>
 * </p>
 * 
 * @see ArticleIndexRebuild
 * @author Administrator
 *
 */
@Component
@Aspect
public class ArticleIndexRebuildAspect extends TransactionSynchronizationAdapter
		implements ApplicationEventPublisherAware {

	private final ThreadLocal<Throwable> throwableLocal = new ThreadLocal<>();

	private static final Class<?>[] NO_NEED_REBUILD_EXCEPTIONS = new Class<?>[] { LogicException.class,
			AuthencationException.class, LockException.class };

	private ApplicationEventPublisher applicationEventPublisher;

	@Before("@annotation(ArticleIndexRebuild)")
	public void before(JoinPoint joinPoint) {
		if (TransactionSynchronizationManager.isSynchronizationActive()
				&& !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
			TransactionSynchronizationManager.registerSynchronization(this);
		}
	}

	@AfterThrowing(pointcut = "@annotation(ArticleIndexRebuild)", throwing = "e")
	public void afterThrow(Throwable e) {
		throwableLocal.set(e);
	}

	@Override
	public void afterCompletion(int status) {
		try {
			if (status == STATUS_ROLLED_BACK && needRebuild()) {
				this.applicationEventPublisher.publishEvent(new ArticleIndexRebuildEvent(this));
			}
		} finally {
			throwableLocal.remove();
		}
	}

	private boolean needRebuild() {
		Throwable ex = throwableLocal.get();
		return ex == null || !ExceptionUtils.getFromChain(ex, NO_NEED_REBUILD_EXCEPTIONS).isPresent();
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}
}