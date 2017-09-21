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
package me.wangkang.blog.web.template.thymeleaf;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.AbstractApplicationContext;
import org.thymeleaf.cache.AbstractCacheManager;
import org.thymeleaf.cache.ExpressionCacheKey;
import org.thymeleaf.cache.ICache;
import org.thymeleaf.cache.ICacheEntryValidityChecker;
import org.thymeleaf.cache.TemplateCacheKey;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.engine.TemplateModel;
import org.thymeleaf.templateresource.ITemplateResource;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import me.wangkang.blog.core.evt.TemplateEvitEvent;
import me.wangkang.blog.web.template.Template;
import me.wangkang.blog.web.template.TemplateService;
import me.wangkang.blog.web.template.thymeleaf.ThymeleafTemplateResolver.TemplateResource;

public class ThymeleafCacheManager extends AbstractCacheManager implements InitializingBean {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private TemplateService templateService;

	private final ICache<TemplateCacheKey, TemplateModel> templateCache = new TemplateCache();
	private final ICache<ExpressionCacheKey, Object> expressionCache = new ExpressionCache();

	private final ThreadPoolExecutor tpe = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>(100));

	public ThymeleafCacheManager() {
		tpe.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
	}

	@Override
	protected ICache<TemplateCacheKey, TemplateModel> initializeTemplateCache() {
		return templateCache;
	}

	@Override
	protected ICache<ExpressionCacheKey, Object> initializeExpressionCache() {
		return expressionCache;
	}

	private final class TemplateCache implements ICache<TemplateCacheKey, TemplateModel> {

		private Cache<TemplateCacheKey, TemplateModel> cache = Caffeine.newBuilder().build();

		@Override
		public void put(TemplateCacheKey key, TemplateModel value) {
			TemplateData templateData = value.getTemplateData();
			ITemplateResource resource = templateData.getTemplateResource();
			if (resource instanceof TemplateResource) {
				final Template template = ((TemplateResource) resource).getTemplate();
				String templateName = templateData.getTemplate();
				tpe.execute(() ->
				/**
				 * 如果是Template，此时应该和当前的Template进行比对，如果一致才放入缓存
				 * 因为如果读写操作并发执行的话，此时的数据可能是旧的数据
				 * 
				 * 这个操作可能是同步的，因此在首次载入时效率可能非常低
				 */
				templateService.compareTemplate(templateName, template, flag -> {
					if (flag) {
						cache.put(key, value);
					}
				}));
			} else {
				cache.put(key, value);
			}
		}

		@Override
		public TemplateModel get(TemplateCacheKey key) {
			return cache.getIfPresent(key);
		}

		@Override
		public TemplateModel get(TemplateCacheKey key,
				ICacheEntryValidityChecker<? super TemplateCacheKey, ? super TemplateModel> validityChecker) {
			return cache.getIfPresent(key);
		}

		@Override
		public void clear() {
			cache.invalidateAll();
		}

		@Override
		public void clearKey(TemplateCacheKey key) {
			cache.invalidate(key);
		}

		@Override
		public Set<TemplateCacheKey> keySet() {
			return cache.asMap().keySet();
		}

	}

	private final class ExpressionCache implements ICache<ExpressionCacheKey, Object> {

		private final Cache<ExpressionCacheKey, Object> cache = Caffeine.newBuilder()
				.expireAfterAccess(30, TimeUnit.MINUTES).build();

		public ExpressionCache() {
			super();
		}

		@Override
		public void put(ExpressionCacheKey key, Object value) {
			cache.put(key, value);
		}

		@Override
		public Object get(ExpressionCacheKey key) {
			return cache.getIfPresent(key);
		}

		@Override
		public Object get(ExpressionCacheKey key,
				ICacheEntryValidityChecker<? super ExpressionCacheKey, ? super Object> validityChecker) {
			return cache.getIfPresent(key);
		}

		@Override
		public void clear() {
			cache.invalidateAll();
		}

		@Override
		public void clearKey(ExpressionCacheKey key) {
			cache.invalidate(key);
		}

		@Override
		public Set<ExpressionCacheKey> keySet() {
			return cache.asMap().keySet();
		}
	}

	private final class ContextCloseListener implements ApplicationListener<ContextClosedEvent> {

		@Override
		public void onApplicationEvent(ContextClosedEvent event) {
			tpe.shutdownNow();
		}

	}

	private final class EvitListener implements ApplicationListener<TemplateEvitEvent> {

		@Override
		public void onApplicationEvent(TemplateEvitEvent event) {
			if (event.clear()) {
				templateCache.clear();
				expressionCache.clear();
			} else {
				String[] templateNames = event.getTemplateNames();
				final Set<TemplateCacheKey> keysToBeRemoved = new HashSet<>(4 * templateNames.length);
				final Set<TemplateCacheKey> templateCacheKeys = templateCache.keySet();
				for (String templateName : templateNames) {
					for (final TemplateCacheKey templateCacheKey : templateCacheKeys) {
						final String ownerTemplate = templateCacheKey.getOwnerTemplate();
						if (ownerTemplate != null) {
							if (ownerTemplate.equals(templateName)) {
								keysToBeRemoved.add(templateCacheKey);
							}
						} else {
							if (templateCacheKey.getTemplate().equals(templateName)) {
								keysToBeRemoved.add(templateCacheKey);
							}
						}
					}
				}
				for (final TemplateCacheKey keyToBeRemoved : keysToBeRemoved) {
					templateCache.clearKey(keyToBeRemoved);
				}
			}
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		EvitListener evitListener = new EvitListener();
		AbstractApplicationContext appContext = (AbstractApplicationContext) applicationContext;
		appContext.addApplicationListener(evitListener);
		appContext.addApplicationListener(new ContextCloseListener());

		AbstractApplicationContext parentContext = (AbstractApplicationContext) applicationContext.getParent();
		parentContext.addApplicationListener(evitListener);
	}
}
