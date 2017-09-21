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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import me.wangkang.blog.core.dao.ArticleDao;
import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.exception.SystemException;

/**
 * 文章缓存
 * 
 * @author Administrator
 *
 */
public class ArticleCache {

	@Autowired
	private ArticleDao articleDao;
	@Autowired
	private PlatformTransactionManager platformTransactionManager;

	private final LoadingCache<Integer, Article> idCache;

	public ArticleCache(int expireAfterAccessSec) {
		if (expireAfterAccessSec < 0) {
			throw new SystemException("expireAfterAccessSec不能小于0");
		}

		idCache = Caffeine.newBuilder().expireAfterAccess(expireAfterAccessSec, TimeUnit.SECONDS)
				.build(new CacheLoader<Integer, Article>() {

					@Override
					public Article load(Integer key) throws Exception {
						return Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
							Article article = articleDao.selectById(key);
							if (article != null && article.isPublished()) {
								return article;
							}
							return null;
						});
					}
				});
	}

	public ArticleCache() {
		this(30 * 60);
	}

	/**
	 * 根据alias查询文章
	 * 
	 * @param alias
	 * @return
	 */
	public Article getArticle(String alias, boolean putInCache) {
		Integer id = articleDao.selectIdByAlias(alias);
		if (id != null) {
			Article art = getArticle(id, putInCache);
			if (art != null && Objects.equals(alias, art.getAlias())) {
				return art;
			}
		}
		return null;
	}

	/**
	 * 根据id查询文章
	 * 
	 * @param id
	 * @return
	 */
	public Article getArticle(Integer id, boolean putInCache) {
		Article article;
		if (putInCache) {
			article = idCache.get(id);
			if (article != null) {
				article = new Article(article);
			}
		} else {
			article = idCache.getIfPresent(id);
			if (article != null) {
				article = new Article(article);
			} else {
				article = articleDao.selectById(id);
			}
		}
		return article;
	}

	/**
	 * 更新文章点击数
	 * 
	 * @param hitsMap
	 */
	public synchronized void updateHits(Map<Integer, Integer> hitsMap) {
		for (Map.Entry<Integer, Integer> it : hitsMap.entrySet()) {
			Article article = idCache.getIfPresent(it.getKey());
			if (article != null) {
				article.setHits(it.getValue());
			}
		}
	}

	public void evit(Integer id) {
		idCache.invalidate(id);
	}

	public void clear() {
		idCache.invalidateAll();
	}

}
