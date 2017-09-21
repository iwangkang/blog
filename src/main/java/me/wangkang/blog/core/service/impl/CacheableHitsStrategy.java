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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;

import me.wangkang.blog.core.dao.ArticleDao;
import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.evt.ArticleEvent;
import me.wangkang.blog.core.evt.EventType;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.impl.ArticleServiceImpl.HitsStrategy;

/**
 * 将点击数缓存起来，每隔一定的时间刷入数据库
 * <p>
 * <b>由于缓存原因，根据点击量查询无法实时的反应当前结果</b>
 * </p>
 * 
 * @author Administrator
 *
 */
public final class CacheableHitsStrategy implements HitsStrategy {

	@Autowired
	private ArticleCache articleCache;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private ArticleIndexer articleIndexer;
	@Autowired
	private SqlSessionFactory sqlSessionFactory;
	/**
	 * 存储所有文章的点击数
	 */
	private final Map<Integer, HitsHandler> hitsMap = new ConcurrentHashMap<>();

	/**
	 * 储存待刷新点击数的文章
	 */
	private final Map<Integer, Boolean> flushMap = new ConcurrentHashMap<>();

	/**
	 * 如果该项为true，那么在flush之前，相同的ip点击只算一次点击量
	 * <p>
	 * 例如我点击一次增加了一次点击量，一分钟后flush，那么我在这一分钟内(ip的不变的情况下)，无论我点击了多少次，都只算一次
	 * </p>
	 */
	private boolean validIp = true;

	/**
	 * 最多保存的ip数，如果达到或超过该数目，将会立即更新
	 */
	private int maxIps = 100;

	/**
	 * 每50条写入数据库
	 */
	private int flushNum = 50;

	@Override
	public void hit(Article article) {
		// increase
		hitsMap.computeIfAbsent(article.getId(), k -> validIp ? new IPBasedHitsHandler(article.getHits(), maxIps)
				: new DefaultHitsHandler(article.getHits())).hit(article);
		flushMap.putIfAbsent(article.getId(), Boolean.TRUE);
	}

	private synchronized void doFlush(List<HitsWrapper> wrappers, boolean contextClose) {
		// 得到当前的实时点击数
		Map<Integer, Integer> hitsMap = wrappers.stream().filter(wrapper -> wrapper.hitsHandler != null)
				.collect(Collectors.toMap(wrapper -> wrapper.id, wrapper -> wrapper.hitsHandler.getHits()));
		if (!hitsMap.isEmpty()) {

			Transactions.executeInTransaction(transactionManager, status -> {
				if (!contextClose) {
					Transactions.afterCommit(() -> {
						int num = 0;
						List<Integer> ids = new ArrayList<>();
						for (Integer id : hitsMap.keySet()) {
							ids.add(id);
							if (++num % flushNum == 0) {
								articleIndexer.addOrUpdateDocument(ids.toArray(new Integer[ids.size()]));
								ids.clear();
							}
						}
						articleIndexer.addOrUpdateDocument(ids.toArray(new Integer[ids.size()]));
						articleCache.updateHits(hitsMap);
					});
				}

				try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
					ArticleDao articleDao = sqlSession.getMapper(ArticleDao.class);
					int num = 0;
					for (Map.Entry<Integer, Integer> it : hitsMap.entrySet()) {
						articleDao.updateHits(it.getKey(), it.getValue());
						num++;
						if (num % flushNum == 0) {
							sqlSession.commit();
						}
					}
					sqlSession.commit();
				}

			});
		}
	}

	private final class HitsWrapper {
		private final Integer id;
		private final HitsHandler hitsHandler;

		public HitsWrapper(Integer id, HitsHandler hitsHandler) {
			super();
			this.id = id;
			this.hitsHandler = hitsHandler;
		}
	}

	private interface HitsHandler {
		void hit(Article article);

		int getHits();
	}

	private final class DefaultHitsHandler implements HitsHandler {

		private final LongAdder adder;

		private DefaultHitsHandler(int init) {
			adder = new LongAdder();
			adder.add(init);
		}

		@Override
		public void hit(Article article) {
			adder.increment();
		}

		@Override
		public int getHits() {
			return adder.intValue();
		}
	}

	private final class IPBasedHitsHandler implements HitsHandler {
		private final Map<String, Boolean> ips = new ConcurrentHashMap<>();
		private final LongAdder adder;
		private final int maxIps;
		private final AtomicInteger counter = new AtomicInteger(0);

		private IPBasedHitsHandler(int init, int maxIps) {
			adder = new LongAdder();
			adder.add(init);
			this.maxIps = maxIps;
		}

		@Override
		public void hit(Article article) {
			String ip = Environment.getIP();
			if (ip != null && ips.putIfAbsent(ip, Boolean.TRUE) == null) {
				adder.increment();
				if (counter.incrementAndGet() >= maxIps) {
					Integer id = article.getId();
					if (flushMap.remove(id) != null) {
						doFlush(Arrays.asList(new HitsWrapper(id, hitsMap.get(id))), false);
					}
				}
			}
		}

		@Override
		public int getHits() {
			return adder.intValue();
		}
	}

	public void flush() {
		flush(false);
	}

	private void flush(boolean contextClose) {
		if (!flushMap.isEmpty()) {
			List<HitsWrapper> wrappers = new ArrayList<>();
			for (Iterator<Entry<Integer, Boolean>> iter = flushMap.entrySet().iterator(); iter.hasNext();) {
				Entry<Integer, Boolean> entry = iter.next();
				Integer key = entry.getKey();

				if (flushMap.remove(key) != null) {
					wrappers.add(new HitsWrapper(key, hitsMap.get(key)));
				}
			}
			doFlush(wrappers, contextClose);
		}
	}

	@EventListener
	public void handleContextEvent(ContextClosedEvent event) {
		flush(true);
	}

	@TransactionalEventListener
	public void handleArticleEvent(ArticleEvent evt) {
		if (EventType.DELETE.equals(evt.getEventType())) {
			evt.getArticles().stream().map(Article::getId).forEach(id -> {
				flushMap.remove(id);
				hitsMap.remove(id);
			});
		}
	}

	public void setValidIp(boolean validIp) {
		this.validIp = validIp;
	}

	public void setMaxIps(int maxIps) {
		if (maxIps <= 0) {
			throw new SystemException("每篇文章允许最多允许保存的ip数应该大于0");
		}
		this.maxIps = maxIps;
	}

	public void setFlushNum(int flushNum) {
		if (flushNum < 1) {
			throw new SystemException("flushNum不能小于1");
		}
		this.flushNum = flushNum;
	}
}