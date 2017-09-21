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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.StampedLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionalEventListener;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.evt.ArticleEvent;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.service.impl.ArticleServiceImpl.ArticleViewedLogger;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.SerializationUtils;

/**
 * 将最近访问的文章纪录在内存中
 * <p>
 * <b>可能文章状态可能会变更，所以实际返回的数量可能小于<i>max</i></b> <br>
 * 
 * <b>如果在记录日志的过程中文章状态发生了变更，并且记录过程发生在处理事件之后，那么被纪录的数据则为脏数据，但这种情况发生的概率非常小<b/>
 * </p>
 * <p>
 * </p>
 * 
 * @author Administrator
 *
 */
public class SyncArticleViewdLogger implements InitializingBean, ArticleViewedLogger {

	private static final Logger LOGGER = LoggerFactory.getLogger(SyncArticleViewdLogger.class);

	private final int max;
	private Map<Integer, Article> articles;
	private StampedLock lock = new StampedLock();

	/**
	 * 应用关闭时当前访问的文章存入文件中
	 */
	private final Path sdfile = Constants.DAT_DIR.resolve("sync_articles_viewd.dat");

	public SyncArticleViewdLogger(int max) {
		if (max < 0) {
			throw new SystemException("max必须大于0");
		}
		this.max = max;

		articles = new LinkedHashMap<Integer, Article>() {

			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Entry<Integer, Article> eldest) {
				return size() > max;
			}

		};
	}

	public SyncArticleViewdLogger() {
		this(10);
	}

	@Override
	public List<Article> getViewdArticles(int num) {
		long stamp = lock.tryOptimisticRead();
		List<Article> result = getCurrentViewed(num);
		if (!lock.validate(stamp)) {
			stamp = lock.readLock();
			try {
				result = getCurrentViewed(num);
			} finally {
				lock.unlockRead(stamp);
			}
		}
		return result;
	}

	private List<Article> getCurrentViewed(int num) {
		List<Article> result = new ArrayList<>(articles.values());
		if (!result.isEmpty()) {
			Collections.reverse(result);
			int finalNum = Math.min(num, max);
			if (result.size() > finalNum) {
				result = result.subList(0, finalNum - 1);
			}
		}
		return result;
	}

	@Override
	public void logViewd(Article article) {
		long stamp = lock.writeLock();
		try {
			articles.remove(article.getId());
			articles.put(article.getId(), article);
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	@TransactionalEventListener
	public void handleArticleEvent(ArticleEvent evt) {
		long stamp = lock.writeLock();
		try {
			switch (evt.getEventType()) {
			case DELETE:
				evt.getArticles().forEach(art -> articles.remove(art.getId()));
				break;
			case UPDATE:
				for (Article art : evt.getArticles()) {
					boolean valid = art.isPublished() && !art.isPrivate();
					if (!valid) {
						articles.remove(art.getId());
					} else {
						articles.replace(art.getId(), new Article(art));
					}
				}
				break;
			default:
				break;
			}
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	@EventListener
	public void handleContextCloseEvent(ContextClosedEvent evt) throws IOException {
		if (!articles.isEmpty()) {
			SerializationUtils.serialize(new LinkedHashMap<>(articles), sdfile);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (FileUtils.exists(sdfile)) {
			try {
				this.articles.putAll(SerializationUtils.deserialize(sdfile));
			} catch (Exception e) {
				LOGGER.warn("反序列化文件" + sdfile + "失败：" + e.getMessage(), e);
			} finally {
				if (!FileUtils.deleteQuietly(sdfile)) {
					LOGGER.warn("删除文件{}失败",sdfile);
				}
			}
		}
	}
}
