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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import me.wangkang.blog.core.config.GlobalConfig;
import me.wangkang.blog.core.dao.ArticleDao;
import me.wangkang.blog.core.dao.ArticleTagDao;
import me.wangkang.blog.core.dao.SpaceDao;
import me.wangkang.blog.core.dao.TagDao;
import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.entity.ArticleTag;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.entity.Tag;
import me.wangkang.blog.core.entity.Article.ArticleStatus;
import me.wangkang.blog.core.evt.ArticleEvent;
import me.wangkang.blog.core.evt.ArticleIndexRebuildEvent;
import me.wangkang.blog.core.evt.EventType;
import me.wangkang.blog.core.evt.LockDeleteEvent;
import me.wangkang.blog.core.evt.SpaceDeleteEvent;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.RuntimeLogicException;
import me.wangkang.blog.core.lock.LockManager;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.pageparam.ArticleQueryParam;
import me.wangkang.blog.core.pageparam.PageResult;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.ArticleService;
import me.wangkang.blog.core.service.CommentServer;
import me.wangkang.blog.core.service.ConfigService;
import me.wangkang.blog.core.vo.ArticleArchiveTree;
import me.wangkang.blog.core.vo.ArticleNav;
import me.wangkang.blog.core.vo.TagCount;
import me.wangkang.blog.core.vo.ArticleArchiveTree.ArticleArchiveMode;

public class ArticleServiceImpl implements ArticleService, InitializingBean, ApplicationEventPublisherAware {

	@Autowired
	private ArticleDao articleDao;
	@Autowired
	private SpaceDao spaceDao;
	@Autowired
	private SpaceCache spaceCache;
	@Autowired
	private ArticleTagDao articleTagDao;
	@Autowired
	private TagDao tagDao;
	@Autowired
	private LockManager lockManager;
	@Autowired
	private ArticleCache articleCache;
	@Autowired
	private ConfigService configService;
	@Autowired
	private CommentServer articleCommentStatisticsService;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private ArticleIndexer articleIndexer;

	private ApplicationEventPublisher applicationEventPublisher;

	private boolean rebuildIndex = true;

	@Autowired(required = false)
	private ArticleContentHandler articleContentHandler;
	private final ScheduleManager scheduleManager = new ScheduleManager();

	/**
	 * 点击策略
	 */
	@Autowired(required = false)
	private HitsStrategy hitsStrategy;

	private ArticleHitManager articleHitManager;

	@Autowired(required = false)
	private ArticleViewedLogger articleViewedLogger;

	@Override
	@Transactional(readOnly = true)
	public Optional<Article> getArticleForView(String idOrAlias) {
		Optional<Article> optionalArticle = getCheckedArticle(idOrAlias, true);
		if (optionalArticle.isPresent()) {

			Article clone = new Article(optionalArticle.get());
			clone.setComments(articleCommentStatisticsService.queryArticleCommentCount(clone.getId()).orElse(0));

			if (articleContentHandler != null) {
				articleContentHandler.handle(clone);
			}

			return Optional.of(clone);
		}
		return Optional.empty();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Article> getArticleForEdit(Integer id) {
		Article article = articleDao.selectById(id);
		return Optional.ofNullable(article);
	}

	@Override
	public void hit(Integer id) {
		articleHitManager.hit(id);
	}

	@Override
	@ArticleIndexRebuild
	@Caching(evict = { @CacheEvict(value = "hotTags", allEntries = true) })
	@Sync
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Article updateArticle(Article article) throws LogicException {
		Space space = spaceCache.checkSpace(article.getSpace().getId());
		article.setSpace(space);
		// 如果文章是私有的，无法设置锁
		if (article.isPrivate()) {
			article.setLockId(null);
		} else {
			lockManager.ensureLockvailable(article.getLockId());
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		Article articleDb = articleDao.selectById(article.getId());
		if (articleDb == null) {
			throw new LogicException("article.notExists", "文章不存在");
		}
		if (articleDb.isDeleted()) {
			throw new LogicException("article.deleted", "文章已经被删除");
		}
		if (article.getAlias() != null) {
			Article aliasDb = articleDao.selectByAlias(article.getAlias());
			if (aliasDb != null && !aliasDb.equals(article)) {
				throw new LogicException("article.alias.exists", "别名" + article.getAlias() + "已经存在",
						article.getAlias());
			}
		}

		if (nochange(article, articleDb)) {
			return articleDb;
		}

		Timestamp pubDate = null;
		switch (article.getStatus()) {
		case DRAFT:
			pubDate = articleDb.isSchedule() ? null : articleDb.getPubDate() != null ? articleDb.getPubDate() : null;
			break;
		case PUBLISHED:
			pubDate = articleDb.isSchedule() ? now : articleDb.getPubDate() != null ? articleDb.getPubDate() : now;
			break;
		case SCHEDULED:
			pubDate = article.getPubDate();
			break;
		default:
			break;
		}

		article.setPubDate(pubDate);

		if (articleDb.getPubDate() != null && article.isPublished()) {
			article.setLastModifyDate(now);
		}

		articleTagDao.deleteByArticle(articleDb);

		articleDao.update(article);

		boolean rebuildIndexWhenTagChange = insertTags(article);

		Transactions.afterCommit(() -> {

			if (article.isSchedule()) {
				scheduleManager.update();
			}

			articleCache.evit(article.getId());
			if (rebuildIndexWhenTagChange) {
				applicationEventPublisher.publishEvent(new ArticleIndexRebuildEvent(this));
			} else {
				articleIndexer.deleteDocument(article.getId());
				if (article.isPublished()) {
					articleIndexer.addOrUpdateDocument(article.getId());
				}
			}
		});
		applicationEventPublisher
				.publishEvent(new ArticleEvent(this, articleDao.selectById(article.getId()), EventType.UPDATE));
		return article;

	}

	@Override
	@ArticleIndexRebuild
	@Caching(evict = { @CacheEvict(value = "hotTags", allEntries = true) })
	@Sync
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Article writeArticle(Article article) throws LogicException {
		Space space = spaceCache.checkSpace(article.getSpace().getId());
		article.setSpace(space);
		// 如果文章是私有的，无法设置锁
		if (article.isPrivate()) {
			article.setLockId(null);
		} else {
			lockManager.ensureLockvailable(article.getLockId());
		}
		if (article.getAlias() != null) {
			Article aliasDb = articleDao.selectByAlias(article.getAlias());
			if (aliasDb != null) {
				throw new LogicException("article.alias.exists", "别名" + article.getAlias() + "已经存在",
						article.getAlias());
			}
		}

		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		Timestamp pubDate = null;
		switch (article.getStatus()) {
		case DRAFT:
			// 如果是草稿
			pubDate = null;
			break;
		case PUBLISHED:
			pubDate = now;
			break;
		case SCHEDULED:
			pubDate = article.getPubDate();
			break;
		default:
			break;
		}
		article.setPubDate(pubDate);

		articleDao.insert(article);

		boolean rebuildIndexWhenTagChange = insertTags(article);
		if (article.isSchedule()) {
			scheduleManager.update();
		}

		Transactions.afterCommit(() -> {
			if (rebuildIndexWhenTagChange) {
				applicationEventPublisher.publishEvent(new ArticleIndexRebuildEvent(this));
			} else {
				if (article.isPublished()) {
					articleIndexer.addOrUpdateDocument(article.getId());
				}
			}
		});
		applicationEventPublisher
				.publishEvent(new ArticleEvent(this, articleDao.selectById(article.getId()), EventType.INSERT));
		return article;
	}

	@Override
	@ArticleIndexRebuild
	@Caching(evict = { @CacheEvict(value = "hotTags", allEntries = true) })
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Article publishDraft(Integer id) throws LogicException {
		Article article = articleDao.selectById(id);
		if (article == null) {
			throw new LogicException("article.notExists", "文章不存在");
		}
		if (!article.isDraft()) {
			throw new LogicException("article.notDraft", "文章已经被删除");
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		article.setPubDate(article.isSchedule() ? now : article.getPubDate() != null ? article.getPubDate() : now);
		article.setStatus(ArticleStatus.PUBLISHED);
		articleDao.update(article);
		Transactions.afterCommit(() -> articleIndexer.addOrUpdateDocument(id));
		applicationEventPublisher.publishEvent(new ArticleEvent(this, article, EventType.UPDATE));
		return article;
	}

	private boolean insertTags(Article article) {
		Set<Tag> tags = article.getTags();
		boolean rebuildIndexWhenTagChange = false;
		if (!CollectionUtils.isEmpty(tags)) {
			for (Tag tag : tags) {
				Tag tagDb = tagDao.selectByName(cleanTag(tag.getName()));
				ArticleTag articleTag = new ArticleTag();
				articleTag.setArticle(article);
				if (tagDb == null) {
					// 插入标签
					tag.setCreate(Timestamp.valueOf(LocalDateTime.now()));
					tag.setName(tag.getName().trim());
					tagDao.insert(tag);
					articleTag.setTag(tag);
					articleIndexer.addTags(tag.getName());
					rebuildIndexWhenTagChange = true;
				} else {
					articleTag.setTag(tagDb);
				}
				articleTagDao.insert(articleTag);
			}
		}
		return rebuildIndexWhenTagChange;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * <b>当允许查询includeSpaces的时候，为了提高效率，首先会通过{@code SpaceCache}查询是否存在这个别名的空间，
	 * 如果存在，则将空间id放入{@code ArticleQueryParam#getIncludeSpaceIds()}中，
	 * 所以，当查询不存在的alias时，依旧能够查询到数据[includeSpaces中不存在的alias不会起作用]</b>
	 * </p>
	 */
	@Override
	@Transactional(readOnly = true)
	public PageResult<Article> queryArticle(ArticleQueryParam param) {
		checkParam(param);

		// 5.5.5
		if (StringUtils.isEmpty(param.getTag())) {
			param.setTagId(null);
		} else {
			Tag tag = tagDao.selectByName(param.getTag());
			if (tag == null) {
				return new PageResult<>(param, 0, Collections.emptyList());
			}
			param.setTagId(tag.getId());
		}

		PageResult<Article> page;
		if (param.hasQuery()) {
			page = articleIndexer.query(param);
		} else {
			int count = articleDao.selectCount(param);
			List<Article> datas = articleDao.selectPage(param);
			page = new PageResult<>(param, count, datas);
		}
		// query comments
		List<Article> datas = page.getDatas();
		if (!CollectionUtils.isEmpty(datas)) {
			List<Integer> ids = datas.stream().map(Article::getId).collect(Collectors.toList());
			Map<Integer, Integer> countsMap = articleCommentStatisticsService.queryArticlesCommentCount(ids);
			for (Article article : datas) {
				Integer comments = countsMap.get(article.getId());
				article.setComments(comments == null ? 0 : comments);
			}
		}
		return page;
	}

	private void checkParam(ArticleQueryParam param) {
		GlobalConfig globalConfig = configService.getGlobalConfig();
		param.setPageSize(Math.min(param.getPageSize(), globalConfig.getArticlePageSize()));
		// 如果查询私有文章，但是用户没有登录
		if (param.isQueryPrivate() && !Environment.isLogin()) {
			param.setQueryPrivate(false);
		}
		// 如果在空间下查询，不能查询多个空间
		if (param.getSpace() != null) {
			param.setSpaceIds(new HashSet<>());
		}
		// 如果查询多个空间
		if (!param.getSpaces().isEmpty()) {
			// 如果空间别名与alias一致，查询这个空间
			Set<Integer> includeSpaceIds = new HashSet<>();
			out: for (Space space : spaceCache.getSpaces(param.isQueryPrivate())) {
				for (String alias : param.getSpaces()) {
					if (alias.equals(space.getAlias())) {
						includeSpaceIds.add(space.getId());
						continue out;
					}
				}
			}
			param.setSpaceIds(includeSpaceIds);
		}
	}

	@Override
	@ArticleIndexRebuild
	@Caching(evict = { @CacheEvict(value = "hotTags", allEntries = true) })
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Article logicDeleteArticle(Integer id) throws LogicException {
		Article article = articleDao.selectById(id);
		if (article == null) {
			throw new LogicException("article.notExists", "文章不存在");
		}
		if (article.isDeleted()) {
			throw new LogicException("article.deleted", "文章已经被删除");
		}
		article.setStatus(ArticleStatus.DELETED);
		articleDao.update(article);

		Transactions.afterCommit(() -> {
			articleCache.evit(id);
			articleIndexer.deleteDocument(id);
		});

		applicationEventPublisher.publishEvent(new ArticleEvent(this, article, EventType.UPDATE));

		return article;
	}

	@Override
	@ArticleIndexRebuild
	@Caching(evict = { @CacheEvict(value = "hotTags", allEntries = true) })
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Article recoverArticle(Integer id) throws LogicException {
		Article article = articleDao.selectById(id);
		if (article == null) {
			throw new LogicException("article.notExists", "文章不存在");
		}
		if (!article.isDeleted()) {
			throw new LogicException("article.undeleted", "文章未删除");
		}
		ArticleStatus status = ArticleStatus.PUBLISHED;
		if (article.getPubDate().after(Timestamp.valueOf(LocalDateTime.now()))) {
			status = ArticleStatus.SCHEDULED;
		}
		article.setStatus(status);
		articleDao.update(article);

		Transactions.afterCommit(() -> articleIndexer.addOrUpdateDocument(id));

		applicationEventPublisher.publishEvent(new ArticleEvent(this, article, EventType.UPDATE));
		return article;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public void deleteArticle(Integer id) throws LogicException {
		Article article = articleDao.selectById(id);
		if (article == null) {
			throw new LogicException("article.notExists", "文章不存在");
		}
		if (!article.isDraft() && !article.isDeleted()) {
			throw new LogicException("article.undeleted", "文章未删除");
		}
		// 删除博客的引用
		articleTagDao.deleteByArticle(article);
		articleDao.deleteById(id);

		applicationEventPublisher.publishEvent(new ArticleEvent(this, article, EventType.DELETE));
	}

	@Override
	@Caching(evict = { @CacheEvict(value = "hotTags", allEntries = true, condition = "#result > 0") })
	public int publishScheduled() {
		return scheduleManager.publish();
	}

	/**
	 * {@inheritDoc}
	 * 
	 * <p>
	 * <b>这个方法只提供根据发布时间排序的上下文章</b>
	 * </p>
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<ArticleNav> getArticleNav(String idOrAlias, boolean queryLock) {
		Optional<Article> optionalArticle = getCheckedArticle(idOrAlias, false);
		if (optionalArticle.isPresent()) {
			Article article = optionalArticle.get();
			if (!Environment.match(article.getSpace())) {
				return Optional.empty();
			}
			boolean queryPrivate = Environment.isLogin();
			Article previous = articleDao.getPreviousArticle(article, queryPrivate, queryLock);
			Article next = articleDao.getNextArticle(article, queryPrivate, queryLock);
			return (previous != null || next != null) ? Optional.of(new ArticleNav(previous, next)) : Optional.empty();
		}
		return Optional.empty();
	}

	@Override
	@Transactional(readOnly = true)
	@Cacheable(value = "hotTags", key = "'hotTags-'+'space-'+(T(me.wangkang.blog.core.security.Environment).getSpace())+'-private-'+(T(me.wangkang.blog.core.security.Environment).isLogin())")
	public List<TagCount> queryTags() throws LogicException {
		return articleTagDao.selectTags(Environment.getSpace(), Environment.isLogin());
	}

	/**
	 * {@inheritDoc}
	 * 
	 * 由于点击后才会被记录，而点击方法和浏览文章的方法是事分离的，因为可能更多的反应的是点击过的文章
	 * 
	 * @param num
	 *            记录数，该记录数受到 {@code ArticleViewdLogger}的限制
	 * @see ArticleViewedLogger#getViewdArticles(int)
	 * @see ArticleHitManager#hit(Integer)
	 */
	@Override
	public List<Article> getRecentlyViewdArticle(int num) {
		return articleViewedLogger == null ? Collections.emptyList()
				: articleViewedLogger.getViewdArticles(Math.max(1, num));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Article> selectRandom(boolean queryLock) {
		return Optional.ofNullable(articleDao.selectRandom(Environment.getSpace(), Environment.isLogin(), queryLock));
	}

	@Override
	public ArticleArchiveTree selectArticleArchives(ArticleArchiveMode mode) {
		List<Article> articles = articleDao.selectSimplePublished(Environment.getSpace(), Environment.isLogin());
		return new ArticleArchiveTree(articles, mode == null ? ArticleArchiveMode.YMD : mode);
	}

	@Override
	public void preparePreview(Article article) {
		if (articleContentHandler != null) {
			articleContentHandler.handlePreview(article);
		}
	}

	@EventListener
	public void handleLockDeleteEvent(LockDeleteEvent event) {
		articleDao.deleteLock(event.getLockId());
	}

	@EventListener
	public void handleSpaceDeleteEvent(SpaceDeleteEvent event) {
		Space deleted = event.getSpace();
		// 查询该空间下是否存在文章
		int count = articleDao.selectCountBySpace(deleted);
		if (count > 0) {
			// 空间下存在文章
			// 移动到默认空间
			Space defaultSpace = spaceDao.selectDefault();
			// 默认空间不存在，无法删除空间
			if (defaultSpace == null) {
				throw new RuntimeLogicException(new Message("space.delete.hasArticles", "空间下存在文章，删除失败"));
			}

			articleDao.moveSpace(deleted, defaultSpace);
			// 清空文章缓存
			Transactions.afterCommit(articleCache::clear);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (rebuildIndex) {
			this.articleIndexer.rebuildIndex();
		}

		if (hitsStrategy == null) {
			hitsStrategy = new DefaultHitsStrategy();
		}

		this.articleHitManager = new ArticleHitManager(hitsStrategy);

		scheduleManager.update();
	}

	private Optional<Article> getCheckedArticle(String idOrAlias, boolean putInCache) {
		Article article = null;
		try {
			int id = Integer.parseInt(idOrAlias);
			article = articleCache.getArticle(id, putInCache);
		} catch (NumberFormatException e) {
			article = articleCache.getArticle(idOrAlias, putInCache);
		}
		if (article != null && article.isPublished() && Environment.match(article.getSpace())) {
			if (article.isPrivate()) {
				Environment.doAuthencation();
			}

			lockManager.openLock(article);
			return Optional.of(article);
		}

		return Optional.empty();
	}

	/**
	 * 查询标签是否存在的时候清除两边空格并且忽略大小写
	 * 
	 * @param tag
	 * @return
	 */
	protected String cleanTag(String tag) {
		return tag.trim().toLowerCase();
	}

	public void setRebuildIndex(boolean rebuildIndex) {
		this.rebuildIndex = rebuildIndex;
	}

	private final class ScheduleManager {
		private Timestamp start;

		public int publish() {
			if (start == null) {
				return 0;
			}
			long now = System.currentTimeMillis();
			if (now < start.getTime()) {
				return 0;
			} else {
				Timestamp startCopy = new Timestamp(start.getTime());
				List<Article> articles = Transactions.executeInTransaction(transactionManager, status -> {
					Transactions.afterRollback(() -> start = startCopy);
					List<Article> schedules = articleDao.selectScheduled(new Timestamp(now));
					if (!schedules.isEmpty()) {
						for (Article article : schedules) {
							article.setStatus(ArticleStatus.PUBLISHED);
							articleDao.update(article);
						}
						applicationEventPublisher.publishEvent(new ArticleEvent(this, schedules, EventType.UPDATE));
					}
					start = articleDao.selectMinimumScheduleDate();
					return schedules;
				});
				articleIndexer.addOrUpdateDocument(articles.stream().map(Article::getId).toArray(i -> new Integer[i]));
				return articles.size();
			}
		}

		public void update() {
			Transactions.executeInReadOnlyTransaction(transactionManager, status -> {
				start = articleDao.selectMinimumScheduleDate();
			});
		}
	}

	private final class ArticleHitManager {

		private final HitsStrategy hitsStrategy;

		public ArticleHitManager(HitsStrategy hitsStrategy) {
			super();
			this.hitsStrategy = hitsStrategy;
		}

		void hit(Integer id) {
			Article article = articleCache.getArticle(id, false);
			if (article != null && validHit(article)) {
				hitsStrategy.hit(article);

				if (articleViewedLogger != null) {
					articleViewedLogger.logViewd(article);
				}
			}
		}

		private boolean validHit(Article article) {
			boolean hit = !Environment.isLogin() && article.isPublished() && Environment.match(article.getSpace())
					&& !article.getIsPrivate();

			if (hit) {
				lockManager.openLock(article);
			}
			return hit;
		}
	}

	/**
	 * 文章缓存点击策略
	 * <p>
	 * 文章可能存在于缓存中，需要在合适的时机手动更新缓存和文章索引
	 * </p>
	 * 
	 * @see ArticleCache#updateHits(Map)
	 * @see ArticleIndexer#addOrUpdateDocument(Integer...)
	 * 
	 * @see DefaultHitsStrategy
	 * @author mhlx
	 *
	 */
	public interface HitsStrategy {
		/**
		 * 点击文章
		 * 
		 * @param article
		 */
		void hit(Article article);
	}

	/**
	 * 默认文章点击策略，文章的点击数将会实时显示
	 * <p>
	 * <b>这种策略下每次点击都会增加点击量</b>
	 * </p>
	 * 
	 * @see CacheableHitsStrategy
	 * @author mhlx
	 *
	 */
	private final class DefaultHitsStrategy implements HitsStrategy {

		@Override
		public void hit(Article article) {
			synchronized (this) {
				int hits = article.getHits() + 1;
				Transactions.executeInTransaction(transactionManager, status -> {
					Integer id = article.getId();
					articleDao.updateHits(id, hits);

					Transactions.afterCommit(() -> {
						Map<Integer, Integer> hitsMap = new HashMap<>();
						hitsMap.put(article.getId(), hits);
						articleCache.updateHits(hitsMap);
						articleIndexer.addOrUpdateDocument(article.getId());
					});
				});
			}
		}
	}

	/**
	 * 用来记录最近被访问的文章
	 * 
	 * @author Administrator
	 *
	 */
	public interface ArticleViewedLogger {

		/**
		 * 查询最近被访问的文章
		 * 
		 * @param num
		 *            文章数量
		 * @return
		 */
		List<Article> getViewdArticles(int num);

		/**
		 * 记录文章
		 * 
		 * @param article
		 */
		void logViewd(Article article);
	}

	/**
	 * 判断文章是否需要更新
	 * 
	 * @param newArticle
	 *            当前文章
	 * @param old
	 *            已经存在的文章
	 * @return
	 */
	protected boolean nochange(Article newArticle, Article old) {
		Objects.requireNonNull(newArticle);
		Objects.requireNonNull(old);
		return Objects.equals(newArticle.getAlias(), old.getAlias())
				&& Objects.equals(newArticle.getAllowComment(), old.getAllowComment())
				&& Objects.equals(newArticle.getContent(), old.getContent())
				&& Objects.equals(newArticle.getFrom(), old.getFrom())
				&& Objects.equals(newArticle.getIsPrivate(), old.getIsPrivate())
				&& Objects.equals(newArticle.getLevel(), old.getLevel())
				&& Objects.equals(newArticle.getLockId(), old.getLockId())
				&& Objects.equals(newArticle.getSpace(), old.getSpace())
				&& Objects.equals(newArticle.getSummary(), old.getSummary())
				&& Objects.equals(newArticle.getTagStr(), old.getTagStr())
				&& Objects.equals(newArticle.getTitle(), old.getTitle())
				&& Objects.equals(newArticle.getStatus(), old.getStatus());
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}
}
