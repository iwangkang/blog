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
package me.wangkang.blog.support.ping;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.CollectionUtils;

import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.evt.ArticleEvent;
import me.wangkang.blog.core.evt.EventType;
import me.wangkang.blog.core.exception.SystemException;

/**
 * 简单的ping管理器
 * 
 * @author Administrator
 *
 */
public class SimplePingManager implements InitializingBean {

	protected static final Logger LOGGER = LoggerFactory.getLogger(SimplePingManager.class);

	private List<PingService> pingServices = new ArrayList<>();
	private final String blogName;

	@Autowired
	@Qualifier("taskExecutor")
	private ThreadPoolTaskExecutor taskExecutor;

	public SimplePingManager(String blogName) {
		super();
		this.blogName = blogName;
	}

	@Async
	@TransactionalEventListener
	public void handleArticleEvent(ArticleEvent event) {
		List<Article> articles = event.getArticles();
		articles.stream().filter(article -> needPing(event.getEventType(), article)).forEach(this::ping);
	}

	private boolean needPing(EventType eventType, Article article) {
		return ((EventType.INSERT.equals(eventType) || EventType.UPDATE.equals(eventType)) && article.isPublished()
				&& !article.hasLock() && !article.isPrivate());

	}

	private void ping(Article article) {
		for (final PingService ps : pingServices) {
			taskExecutor.execute(() -> {
				try {
					ps.ping(article, blogName);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}
			});
		}
	}

	public void setPingServices(List<PingService> pingServices) {
		this.pingServices = pingServices;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (CollectionUtils.isEmpty(pingServices)) {
			throw new SystemException("ping服务不能为空");
		}
	}

}
