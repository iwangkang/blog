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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import me.wangkang.blog.core.config.CommentConfig;
import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.dao.ArticleDao;
import me.wangkang.blog.core.dao.CommentDao;
import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.entity.Comment;
import me.wangkang.blog.core.entity.CommentMode;
import me.wangkang.blog.core.entity.CommentModule;
import me.wangkang.blog.core.entity.Editor;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.entity.User;
import me.wangkang.blog.core.entity.Comment.CommentStatus;
import me.wangkang.blog.core.entity.CommentModule.ModuleType;
import me.wangkang.blog.core.evt.ArticleEvent;
import me.wangkang.blog.core.evt.CommentEvent;
import me.wangkang.blog.core.evt.EventType;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.lock.LockManager;
import me.wangkang.blog.core.pageparam.CommentQueryParam;
import me.wangkang.blog.core.pageparam.PageQueryParam;
import me.wangkang.blog.core.pageparam.PageResult;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.security.input.HtmlClean;
import me.wangkang.blog.core.security.input.Markdown2Html;
import me.wangkang.blog.core.service.CommentServer;
import me.wangkang.blog.core.service.UserQueryService;
import me.wangkang.blog.core.vo.CommentPageResult;
import me.wangkang.blog.core.vo.Limit;
import me.wangkang.blog.core.vo.ModuleCommentCount;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.Resources;
import me.wangkang.blog.web.template.Page;
import me.wangkang.blog.web.template.PageEvent;
import me.wangkang.blog.web.template.dao.PageDao;

public class CommentService implements InitializingBean, CommentServer, ApplicationEventPublisherAware {

	@Autowired(required = false)
	private CommentChecker commentChecker;
	@Autowired
	private HtmlClean htmlClean;
	@Autowired
	protected CommentDao commentDao;
	@Autowired
	protected UrlHelper urlHelper;
	@Autowired
	private ArticleCache articleCache;
	@Autowired
	private LockManager lockManager;
	@Autowired
	private PageDao pageDao;
	@Autowired
	private Markdown2Html markdown2Html;
	@Autowired
	private UserQueryService userQueryService;
	@Autowired
	private ArticleDao articleDao;

	private ApplicationEventPublisher applicationEventPublisher;

	/**
	 * 评论配置项
	 */
	protected static final String COMMENT_MODE = "commentConfig.commentMode";
	protected static final String COMMENT_ASC = "commentConfig.commentAsc";
	protected static final String COMMENT_EDITOR = "commentConfig.editor";
	protected static final String COMMENT_LIMIT_SEC = "commentConfig.commentLimitSec";
	protected static final String COMMENT_LIMIT_COUNT = "commentConfig.commentLimitCount";
	protected static final String COMMENT_CHECK = "commentConfig.commentCheck";
	protected static final String COMMENT_PAGESIZE = "commentConfig.commentPageSize";

	private final Comparator<Comment> ascCommentComparator = Comparator.comparing(Comment::getCommentDate)
			.thenComparing(Comment::getId);
	private final Comparator<Comment> descCommentComparator = (t1, t2) -> -ascCommentComparator.compare(t1, t2);

	/**
	 * 为了保证一个树结构，这里采用 path来纪录层次结构
	 * {@link http://stackoverflow.com/questions/4057947/multi-tiered-comment-replies-display-and-storage}.
	 * 同时为了走索引，只能限制它为255个字符，由于id为数字的原因，实际上一般情况下很难达到255的长度(即便id很大)，所以这里完全够用
	 */
	private static final int PATH_MAX_LENGTH = 255;

	/**
	 * 评论配置文件位置
	 */

	private static final Path RES_PATH = Constants.CONFIG_DIR.resolve("commentConfig.properties");
	private final Resource configResource = new PathResource(RES_PATH);
	private final Properties pros = new Properties();

	private CommentConfig config;

	static {
		FileUtils.createFile(RES_PATH);
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		if (commentChecker == null) {
			commentChecker = (comment, config) -> {
			};
		}

		Resources.readResource(configResource, pros::load);
		loadConfig();
	}

	/**
	 * 审核评论
	 * 
	 * @param id
	 *            评论id
	 * @return
	 * @throws LogicException
	 */

	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Comment checkComment(Integer id) throws LogicException {
		Comment comment = commentDao.selectById(id);// 查询父评论
		if (comment == null) {
			throw new LogicException("comment.notExists", "评论不存在");
		}
		if (!comment.isChecking()) {
			throw new LogicException("comment.checked", "评论审核过了");
		}
		commentDao.updateStatusToNormal(comment);

		return comment;
	}

	/**
	 * 删除评论
	 * 
	 * @param id
	 *            评论id
	 * @throws LogicException
	 */
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public void deleteComment(Integer id) throws LogicException {
		Comment comment = commentDao.selectById(id);
		if (comment == null) {
			throw new LogicException("comment.notExists", "评论不存在");
		}
		commentDao.deleteByPath(comment.getParentPath() + comment.getId());
		commentDao.deleteById(id);
	}

	/**
	 * 更新评论配置
	 * 
	 * @param config
	 *            配置
	 */
	public synchronized CommentConfig updateCommentConfig(CommentConfig config) {
		pros.setProperty(COMMENT_EDITOR, config.getEditor().name());
		pros.setProperty(COMMENT_CHECK, config.getCheck().toString());
		pros.setProperty(COMMENT_LIMIT_COUNT, config.getLimitCount().toString());
		pros.setProperty(COMMENT_LIMIT_SEC, config.getLimitSec().toString());
		pros.setProperty(COMMENT_PAGESIZE, config.getPageSize() + "");
		try (OutputStream os = new FileOutputStream(configResource.getFile())) {
			pros.store(os, "");
		} catch (IOException e) {
			throw new SystemException(e.getMessage(), e);
		}
		loadConfig();
		return config;
	}

	/**
	 * 获取评论配置
	 * 
	 * @return
	 */
	public CommentConfig getCommentConfig() {
		return new CommentConfig(config);
	}

	/**
	 * 新增一条评论
	 * 
	 * @param comment
	 * @return
	 * @throws LogicException
	 */
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Comment insertComment(Comment comment) throws LogicException {
		CommentModule module = comment.getCommentModule();
		switch (module.getType()) {
		case ARTICLE:
			doArticleCommentValid(module.getId());
			break;
		case USERPAGE:
			doPageCommmentValid(module.getId());
			break;
		default:
			throw new SystemException("无效的ModuleType:" + comment.getCommentModule().getType());
		}
		long now = System.currentTimeMillis();
		String ip = comment.getIp();
		if (!Environment.isLogin()) {
			// 检查频率
			Limit limit = config.getLimit();
			long start = now - limit.getUnit().toMillis(limit.getTime());
			int count = commentDao.selectCountByIpAndDatePeriod(comment.getCommentModule(), new Timestamp(start),
					new Timestamp(now), ip) + 1;
			if (count > limit.getCount()) {
				throw new LogicException("comment.overlimit", "评论太过频繁，请稍作休息");
			}
		}

		commentChecker.checkComment(comment, config);

		String parentPath = "/";
		// 判断是否存在父评论
		Comment parent = comment.getParent();
		if (parent != null) {
			parent = commentDao.selectById(parent.getId());// 查询父评论
			if (parent == null) {
				throw new LogicException("comment.parent.notExists", "父评论不存在");
			}

			// 如果父评论正在审核
			if (parent.isChecking()) {
				throw new LogicException("comment.parent.checking", "父评论正在审核");
			}

			if (!comment.matchParent(parent)) {
				throw new LogicException("comment.parent.unmatch", "评论匹配失败");
			}
			parentPath = parent.getParentPath() + parent.getId() + "/";
		}
		if (parentPath.length() > PATH_MAX_LENGTH) {
			throw new LogicException("comment.path.toolong", "该评论不能再被回复了");
		}

		Comment last = commentDao.selectLast(comment);
		if (last != null && last.getContent().equals(comment.getContent())) {
			throw new LogicException("comment.content.same", "已经回复过相同的评论了");
		}

		if (!Environment.isLogin()) {
			String email = comment.getEmail();
			if (email != null) {
				// set gravatar md5
				comment.setGravatar(DigestUtils.md5DigestAsHex(email.getBytes(Constants.CHARSET)));
			}
			comment.setAdmin(false);

		} else {
			// 管理员回复无需设置评论用户信息
			comment.setEmail(null);
			comment.setNickname(null);
			comment.setAdmin(true);
			comment.setWebsite(null);
		}

		comment.setParentPath(parentPath);
		comment.setCommentDate(new Timestamp(now));

		boolean check = config.getCheck() && !Environment.isLogin();
		comment.setStatus(check ? CommentStatus.CHECK : CommentStatus.NORMAL);
		// 获取当前设置的编辑器
		comment.setEditor(config.getEditor());
		comment.setParent(parent);

		commentDao.insert(comment);

		completeComment(comment);

		applicationEventPublisher.publishEvent(new CommentEvent(this, comment));

		return comment;
	}

	@Transactional(readOnly = true)
	public CommentPageResult queryComment(CommentQueryParam param) {
		param.setPageSize(Math.min(config.getPageSize(), param.getPageSize()));
		if (!param.complete()) {
			return new CommentPageResult(param, 0, Collections.emptyList(), new CommentConfig(config));
		}
		CommentModule module = param.getModule();

		if (!doValidateBeforeQuery(module)) {
			return new CommentPageResult(param, 0, Collections.emptyList(), new CommentConfig(config));
		}

		CommentMode mode = param.getMode();
		int count;
		switch (mode) {
		case TREE:
			count = commentDao.selectCountWithTree(param);
			break;
		default:
			count = commentDao.selectCountWithList(param);
			break;
		}
		int pageSize = config.getPageSize();
		if (count == 0) {
			return new CommentPageResult(param, 0, Collections.emptyList(), new CommentConfig(config));
		}
		boolean asc = param.isAsc();
		if (param.getCurrentPage() <= 0) {
			if (asc) {
				param.setCurrentPage(count % pageSize == 0 ? count / pageSize : count / pageSize + 1);
			} else {
				param.setCurrentPage(1);
			}
		}
		List<Comment> datas;
		switch (mode) {
		case TREE:
			datas = commentDao.selectPageWithTree(param);
			for (Comment comment : datas) {
				completeComment(comment);
			}
			datas = handleTree(datas, param.isAsc());
			break;
		default:
			datas = commentDao.selectPageWithList(param);
			for (Comment comment : datas) {
				completeComment(comment);
			}
			break;
		}
		return new CommentPageResult(param, count, datas, new CommentConfig(config));
	}

	/**
	 * 删除评论
	 * 
	 * @param ip
	 * @param referenceId
	 * @throws LogicException
	 */
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public void deleteComment(CommentModule module) {
		commentDao.deleteByModule(module);
	}

	/**
	 * 查询<b>当前空间</b>下 某个模块类型的最近的评论
	 * <p>
	 * <b>用于DataTag</b>
	 * </p>
	 * 
	 * @param type
	 *            模块类型
	 * @param limit
	 *            数目限制
	 * @param queryAdmin
	 *            是否包含管理员
	 * @return
	 */
	@Transactional(readOnly = true)
	public List<Comment> queryLastComments(CommentModule module, int limit, boolean queryAdmin) {
		if (module.getType() == null) {
			return Collections.emptyList();
		}
		List<Comment> comments = commentDao.selectLastComments(module, Environment.getSpace(), limit,
				Environment.isLogin(), queryAdmin);
		for (Comment comment : comments) {
			completeComment(comment);
		}
		return comments;
	}

	@EventListener
	public void handleArticleEvent(ArticleEvent articleEvent) {
		if (articleEvent.getEventType().equals(EventType.DELETE)) {
			List<Article> articles = articleEvent.getArticles();
			for (Article article : articles) {
				CommentModule module = new CommentModule(ModuleType.ARTICLE, article.getId());
				deleteComment(module);
			}
		}
	}

	@EventListener
	public void handlePageEvent(PageEvent event) {
		if (event.getType().equals(EventType.DELETE)) {
			deleteComment(new CommentModule(ModuleType.USERPAGE, event.getPage().getId()));
		}
	}

	/**
	 * 查询会话
	 * 
	 * @param articleId
	 *            文章id
	 * @param id
	 *            当前评论id
	 * @return
	 * @throws LogicException
	 */
	@Transactional(readOnly = true)
	public List<Comment> queryConversations(CommentModule module, Integer id) throws LogicException {
		if (!doValidateBeforeQuery(module)) {
			return Collections.emptyList();
		}
		Comment comment = commentDao.selectById(id);
		if (comment == null) {
			throw new LogicException("comment.notExists", "评论不存在");
		}
		if (!comment.getCommentModule().equals(module)) {
			return Collections.emptyList();
		}
		completeComment(comment);
		if (comment.getParents().isEmpty()) {
			return Arrays.asList(comment);
		}
		List<Comment> comments = new ArrayList<>();
		for (Integer pid : comment.getParents()) {
			Comment p = commentDao.selectById(pid);
			completeComment(p);
			comments.add(p);
		}
		comments.add(comment);
		return comments;
	}

	/**
	 * 获取最终的跳转地址
	 * 
	 * @param module
	 *            评论模块
	 * @return
	 */
	@Transactional(readOnly = true)
	public Optional<String> getLink(CommentModule module) {
		String link = null;
		switch (module.getType()) {
		case ARTICLE:
			Article article = articleCache.getArticle(module.getId(), false);
			if (article != null) {
				link = urlHelper.getUrls().getUrl(article);
			}
			break;
		case USERPAGE:
			Page page = pageDao.selectById(module.getId());
			if (page != null) {
				link = urlHelper.getUrls().getUrl(page);
			}
			break;
		default:
			break;
		}
		return Optional.ofNullable(link);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Integer, Integer> queryArticlesCommentCount(List<Integer> ids) {
		List<CommentModule> modules = ids.stream().map(id -> new CommentModule(ModuleType.ARTICLE, id))
				.collect(Collectors.toList());
		return commentDao.selectCommentCounts(modules).stream()
				.collect(Collectors.toMap(ModuleCommentCount::getModuleId, ModuleCommentCount::getComments));
	}

	@Override
	@Transactional(readOnly = true)
	public OptionalInt queryArticleCommentCount(Integer id) {
		ModuleCommentCount count = commentDao.selectCommentCount(new CommentModule(ModuleType.ARTICLE, id));
		return count == null ? OptionalInt.empty() : OptionalInt.of(count.getComments());
	}

	@Override
	@Transactional(readOnly = true)
	public int queryArticlesTotalCommentCount(Space space, boolean queryPrivate) {
		return commentDao.selectTotalCommentCount(ModuleType.ARTICLE, space, queryPrivate);
	}

	@Override
	@Transactional(readOnly = true)
	public int queryPagesTotalCommentCount(Space space, boolean queryPrivate) {
		return commentDao.selectTotalCommentCount(ModuleType.USERPAGE, space, queryPrivate);
	}

	/**
	 * 查询未审核评论的数目
	 * @since 5.5.6
	 * @return
	 */
	@Transactional
	public int queryUncheckCommentCount(){
		return commentDao.queryUncheckCommentsCount();
	}

	/**
	 * 分页查询待审核评论
	 * 
	 * @param param
	 * @return
	 */
	@Transactional(readOnly = true)
	public PageResult<Comment> queryUncheckComments(PageQueryParam param) {
		param.setPageSize(Math.min(config.getPageSize(), param.getPageSize()));
		int count = commentDao.queryUncheckCommentsCount();
		List<Comment> comments = commentDao.queryUncheckComments(param);
		Map<ModuleType, List<CommentModule>> moduleMap = comments.stream().map(Comment::getCommentModule)
				.collect(Collectors.groupingBy(CommentModule::getType));
		Map<CommentModule, Object> referenceMap = new HashMap<>();
		for (Map.Entry<ModuleType, List<CommentModule>> it : moduleMap.entrySet()) {
			switch (it.getKey()) {
			case ARTICLE:
				List<Article> articles = it.getValue().isEmpty() ? Collections.emptyList()
						: articleDao.selectSimpleByIds(
								it.getValue().stream().map(CommentModule::getId).collect(Collectors.toSet()));
				for (Article article : articles) {
					referenceMap.put(new CommentModule(ModuleType.ARTICLE, article.getId()), article);
				}
				break;
			case USERPAGE:
				List<Page> pages = it.getValue().isEmpty() ? Collections.emptyList()
						: pageDao.selectSimpleByIds(
								it.getValue().stream().map(CommentModule::getId).collect(Collectors.toSet()));
				for (Page page : pages) {
					referenceMap.put(new CommentModule(ModuleType.USERPAGE, page.getId()), page);
				}
				break;
			default:
				throw new SystemException("无法处理的评论模块类型：" + it.getKey());
			}
		}
		for (Comment comment : comments) {
			CommentModule module = comment.getCommentModule();
			module.setObject(referenceMap.get(module));
			completeComment(comment);
		}

		return new PageResult<>(param, count, comments);
	}

	/**
	 * 获取评论链接
	 * 
	 * @param comment
	 * @return
	 */
	protected String getCommentLink(Comment comment) {
		CommentModule module = comment.getCommentModule();
		return new StringBuilder(urlHelper.getUrl()).append("/comment/link/")
				.append(module.getType().name().toLowerCase()).append("/").append(module.getId()).toString();
	}

	private boolean doValidateBeforeQuery(CommentModule module) {
		boolean valid = false;
		switch (module.getType()) {
		case ARTICLE:
			valid = doValidaBeforeQueryArticleComment(module.getId());
			break;
		case USERPAGE:
			valid = doValidaBeforeQueryPageComment(module.getId());
			break;
		default:
			throw new SystemException("无效的ModuleType:" + module.getType());
		}
		return valid;
	}

	private boolean doValidaBeforeQueryArticleComment(Integer moduleId) {
		Article article = articleCache.getArticle(moduleId, false);
		if (article == null || !article.isPublished()) {
			return false;
		}
		if (article.isPrivate()) {
			Environment.doAuthencation();
		}
		if (!Environment.match(article.getSpace())) {
			return false;
		}
		lockManager.openLock(article);
		lockManager.openLock(article.getSpace());
		return true;
	}

	private boolean doValidaBeforeQueryPageComment(Integer moduleId) {
		Page page = pageDao.selectById(moduleId);
		return page != null && Environment.match(page.getSpace());
	}

	private void doArticleCommentValid(Integer moduleId) throws LogicException {
		Article article = articleCache.getArticle(moduleId, false);
		// 博客不存在
		if (article == null || !Environment.match(article.getSpace()) || !article.isPublished()) {
			throw new LogicException("article.notExists", "文章不存在");
		}
		// 如果私人文章并且没有登录
		if (article.isPrivate()) {
			Environment.doAuthencation();
		}
		if (!article.getAllowComment() && !Environment.isLogin()) {
			throw new LogicException("article.notAllowComment", "文章不允许被评论");
		}
		lockManager.openLock(article);
	}

	private void doPageCommmentValid(Integer moduleId) throws LogicException {
		Page page = pageDao.selectById(moduleId);
		if (page == null) {
			throw new LogicException("page.user.notExists", "页面不存在");
		}
		if (!page.getAllowComment() && !Environment.isLogin()) {
			throw new LogicException("page.notAllowComment", "页面不允许评论");
		}
	}

	private List<Comment> buildTree(List<Comment> comments) {
		CollectFilteredFilter filter = new CollectFilteredFilter(null);
		List<Comment> roots = new ArrayList<>();
		comments.stream().filter(filter).collect(Collectors.toList())
				.forEach(comment -> roots.add(pickByParent(comment, filter.rests)));
		return roots;
	}

	private Comment pickByParent(Comment parent, List<Comment> comments) {
		Objects.requireNonNull(parent);
		CollectFilteredFilter filter = new CollectFilteredFilter(parent);
		List<Comment> children = comments.stream().filter(filter).collect(Collectors.toList());
		children.forEach(child -> pickByParent(child, filter.rests));
		parent.setChildren(children);
		return parent;
	}

	private List<Comment> handleTree(List<Comment> comments, boolean asc) {
		if (comments.isEmpty()) {
			return comments;
		}
		List<Comment> tree = buildTree(comments);
		tree.sort(asc ? ascCommentComparator : descCommentComparator);
		return tree;
	}

	private void completeComment(Comment comment) {
		String content = comment.getContent();
		if (comment.getEditor().equals(Editor.MD)) {
			content = markdown2Html.toHtml(comment.getContent());
		}
		comment.setContent(htmlClean.clean(content));
		comment.setUrl(getCommentLink(comment));
		Comment p = comment.getParent();
		if (p != null) {
			fillComment(p);
		}
		fillComment(comment);
	}

	private void fillComment(Comment comment) {
		if (comment.getAdmin() == null || !comment.getAdmin()) {
			return;
		}
		User user = userQueryService.getUser();
		comment.setNickname(user.getName());
		String email = user.getEmail();
		comment.setEmail(email);
		comment.setWebsite(urlHelper.getUrl());
		comment.setGravatar(user.getGravatar());
	}

	private void loadConfig() {
		config = new CommentConfig();
		config.setEditor(Editor.valueOf(pros.getProperty(COMMENT_EDITOR, "MD")));
		config.setCheck(Boolean.parseBoolean(pros.getProperty(COMMENT_CHECK, "false")));
		config.setLimitCount(Integer.parseInt(pros.getProperty(COMMENT_LIMIT_COUNT, "10")));
		config.setLimitSec(Integer.parseInt(pros.getProperty(COMMENT_LIMIT_SEC, "60")));
		config.setPageSize(Integer.parseInt(pros.getProperty(COMMENT_PAGESIZE, "10")));
	}

	private final class CollectFilteredFilter implements Predicate<Comment> {
		private final Comment parent;
		private List<Comment> rests = new ArrayList<>();

		public CollectFilteredFilter(Comment parent) {
			this.parent = parent;
		}

		@Override
		public boolean test(Comment t) {
			if (Objects.equals(parent, t.getParent())) {
				return true;
			}
			rests.add(t);
			return false;
		}
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

}
