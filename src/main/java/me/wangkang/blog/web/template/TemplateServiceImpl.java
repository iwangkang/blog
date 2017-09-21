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
package me.wangkang.blog.web.template;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.evt.EventType;
import me.wangkang.blog.core.evt.SpaceDeleteEvent;
import me.wangkang.blog.core.evt.TemplateEvitEvent;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.pageparam.FragmentQueryParam;
import me.wangkang.blog.core.pageparam.PageResult;
import me.wangkang.blog.core.pageparam.TemplatePageQueryParam;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.ConfigService;
import me.wangkang.blog.core.service.impl.SpaceCache;
import me.wangkang.blog.core.service.impl.Transactions;
import me.wangkang.blog.core.templatedata.DataTagProcessor;
import me.wangkang.blog.core.vo.DataBind;
import me.wangkang.blog.core.vo.DataTag;
import me.wangkang.blog.core.vo.ImportRecord;
import me.wangkang.blog.core.vo.PathTemplateBean;
import me.wangkang.blog.core.vo.PathTemplateLoadRecord;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.StringUtils;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.TemplateView;
import me.wangkang.blog.web.Webs;
import me.wangkang.blog.web.template.TemplateRequestMappingHandlerMapping.MappingRegistry;
import me.wangkang.blog.web.template.dao.FragmentDao;
import me.wangkang.blog.web.template.dao.PageDao;
import me.wangkang.blog.web.validator.FragmentValidator;
import me.wangkang.blog.web.validator.PageValidator;

/**
 * 模板服务类
 * <p>
 * 这个类所有对模板写操作的方法都是加锁的，因此在首次加载模板的时候效率很低
 * </p>
 * <p>
 * <b>这个类必须在Web环境中注册</b>
 * </p>
 * 
 * @author mhlx
 *
 */
public class TemplateServiceImpl implements TemplateService, ApplicationEventPublisherAware, InitializingBean {

	@Autowired
	private PageDao pageDao;
	@Autowired
	private FragmentDao fragmentDao;
	@Autowired
	private SpaceCache spaceCache;
	@Autowired
	private ConfigService configService;

	@Autowired
	private PlatformTransactionManager platformTransactionManager;

	@Autowired
	private ApplicationContext applicationContext;

	private ApplicationEventPublisher applicationEventPublisher;

	private List<DataTagProcessor<?>> processors = new ArrayList<>();

	private static final Message USER_PAGE_NOT_EXISTS = new Message("page.user.notExists", "自定义页面不存在");

	private static final Logger LOGGER = LoggerFactory.getLogger(TemplateServiceImpl.class);

	/**
	 * 系统默认模板片段
	 */
	private List<Fragment> fragments = new ArrayList<>();

	// PathTemplate
	private Path pathTemplateRoot;
	private boolean enablePathTemplate;
	private PathTemplateServiceImpl pathTemplateService;

	// TEMPLATE REGISTER
	@Autowired
	private TemplateRequestMappingHandlerMapping requestMapping;

	private TemplateMappingRegister templateMappingRegister;

	private Map<String, SystemTemplate> defaultTemplates;

	private final List<TemplateProcessor> templateProcessors = new ArrayList<>();
	private PreviewManager previewManager;

	/**
	 * 模板自增长id，用于模板路径冲突时确定模板优先级
	 */
	private final AtomicInteger templateIdGenerator = new AtomicInteger(0);

	@Override
	public synchronized Fragment insertFragment(Fragment fragment) throws LogicException {
		return Transactions.executeInTransaction(platformTransactionManager, status -> {
			checkSpace(fragment);
			Fragment db;
			if (fragment.isGlobal()) {
				db = fragmentDao.selectGlobalByName(fragment.getName());
			} else {
				db = fragmentDao.selectBySpaceAndName(fragment.getSpace(), fragment.getName());
			}
			boolean nameExists = db != null;
			if (nameExists) {
				throw new LogicException("fragment.user.nameExists", "挂件名:" + fragment.getName() + "已经存在",
						fragment.getName());
			}

			fragment.setCreateDate(Timestamp.valueOf(LocalDateTime.now()));
			fragmentDao.insert(fragment);
			evitFragmentCache(fragment.getName());
			return fragment;
		});
	}

	@Override
	public synchronized void deleteFragment(Integer id) throws LogicException {
		Transactions.executeInTransaction(platformTransactionManager, status -> {
			Fragment fragment = fragmentDao.selectById(id);
			if (fragment == null) {
				throw new LogicException("fragment.user.notExists", "挂件不存在");
			}
			fragmentDao.deleteById(id);

			evitFragmentCache(fragment.getName());
		});
	}

	@Override
	public synchronized Fragment updateFragment(Fragment fragment) throws LogicException {
		return Transactions.executeInTransaction(platformTransactionManager, status -> {
			checkSpace(fragment);
			Fragment old = fragmentDao.selectById(fragment.getId());
			if (old == null) {
				throw new LogicException("fragment.user.notExists", "挂件不存在");
			}
			Fragment db;
			// 查找当前数据库是否存在同名
			if (fragment.isGlobal()) {
				db = fragmentDao.selectGlobalByName(fragment.getName());
			} else {
				db = fragmentDao.selectBySpaceAndName(fragment.getSpace(), fragment.getName());
			}
			boolean nameExists = db != null && !db.getId().equals(fragment.getId());
			if (nameExists) {
				throw new LogicException("fragment.user.nameExists", "挂件名:" + fragment.getName() + "已经存在",
						fragment.getName());
			}
			fragmentDao.update(fragment);
			if (old.getName().endsWith(fragment.getName())) {
				evitFragmentCache(old.getName());
			} else {
				evitFragmentCache(old.getName(), fragment.getName());
			}
			return fragment;
		});
	}

	private void checkSpace(Fragment fragment) throws LogicException {
		Space space = fragment.getSpace();
		if (space != null) {
			fragment.setSpace(spaceCache.checkSpace(space.getId()));
		}
	}

	@Override
	public PageResult<Fragment> queryFragment(FragmentQueryParam param) {
		return Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
			param.setPageSize(configService.getGlobalConfig().getFragmentPageSize());
			int count = fragmentDao.selectCount(param);
			List<Fragment> datas = fragmentDao.selectPage(param);
			return new PageResult<>(param, count, datas);
		});
	}

	@Override
	public Optional<Fragment> queryFragment(Integer id) {
		return Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
			return Optional.ofNullable(fragmentDao.selectById(id));
		});
	}

	@Override
	public Optional<Page> queryPage(Integer id) {
		return Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
			return Optional.ofNullable(pageDao.selectById(id));
		});
	}

	@Override
	public PageResult<Page> queryPage(TemplatePageQueryParam param) {
		return Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
			param.setPageSize(configService.getGlobalConfig().getPagePageSize());
			int count = pageDao.selectCount(param);
			List<Page> datas = pageDao.selectPage(param);
			return new PageResult<>(param, count, datas);
		});
	}

	@Override
	public synchronized void deletePage(Integer id) throws LogicException {
		Transactions.executeInTransaction(platformTransactionManager, status -> {
			Page db = pageDao.selectById(id);
			if (db == null) {
				throw new LogicException(USER_PAGE_NOT_EXISTS);
			}
			pageDao.deleteById(id);
			String templateName = db.getTemplateName();
			evitPageCache(templateName);
			this.applicationEventPublisher.publishEvent(new PageEvent(this, EventType.DELETE, db));
			new PageRequestMappingRegisterHelper().unregisterPage(db);
		});
	}

	@Override
	public List<String> queryDataTags() {
		return processors.stream().map(DataTagProcessor::getName).collect(Collectors.toList());
	}

	@Override
	public synchronized Page createPage(Page page) throws LogicException {
		return Transactions.executeInTransaction(platformTransactionManager, status -> {
			enablePageAliasNotContainsSpace(page.getAlias());
			PageRequestMappingRegisterHelper helper = new PageRequestMappingRegisterHelper();
			Space space = page.getSpace();
			if (space != null) {
				page.setSpace(spaceCache.checkSpace(space.getId()));
			}

			String alias = page.getAlias();
			// 检查
			Page aliasPage = pageDao.selectBySpaceAndAlias(page.getSpace(), alias);
			if (aliasPage != null) {
				throw new LogicException("page.user.aliasExists", "别名" + alias + "已经存在", alias);
			}
			page.setCreateDate(Timestamp.valueOf(LocalDateTime.now()));
			pageDao.insert(page);

			evitPageCache(page);
			// 注册现在的页面
			helper.registerPage(page);
			this.applicationEventPublisher.publishEvent(new PageEvent(this, EventType.INSERT, page));
			return page;
		});
	}

	@Override
	public synchronized Page updatePage(Page page) throws LogicException {
		return Transactions.executeInTransaction(platformTransactionManager, status -> {
			enablePageAliasNotContainsSpace(page.getAlias());
			final PageRequestMappingRegisterHelper helper = new PageRequestMappingRegisterHelper();
			Space space = page.getSpace();
			if (space != null) {
				page.setSpace(spaceCache.checkSpace(space.getId()));
			}
			Page db = pageDao.selectById(page.getId());
			if (db == null) {
				throw new LogicException(USER_PAGE_NOT_EXISTS);
			}
			String alias = page.getAlias();
			// 检查
			Page aliasPage = pageDao.selectBySpaceAndAlias(page.getSpace(), alias);
			if (aliasPage != null && !aliasPage.getId().equals(page.getId())) {
				throw new LogicException("page.user.aliasExists", "别名" + alias + "已经存在", alias);
			}
			pageDao.update(page);

			evitPageCache(db);

			// 解除以前的mapping
			helper.unregisterPage(db);
			// 注册现在的页面
			helper.registerPage(page);

			this.applicationEventPublisher.publishEvent(new PageEvent(this, EventType.UPDATE, page));
			return page;
		});
	}

	/**
	 * 确保用户自定页面路径中不包含space信息
	 * 
	 * @param alias
	 * @throws LogicException
	 */
	private void enablePageAliasNotContainsSpace(String alias) throws LogicException {
		if (Webs.getSpaceFromPath(alias) != null) {
			throw new LogicException("page.alias.containsSpace", "路径中不能包含space信息");
		}
	}

	@Override
	public Optional<DataBind> queryData(DataTag dataTag, boolean onlyCallable) throws LogicException {
		Optional<DataTagProcessor<?>> processor = processors.stream()
				.filter(pro -> pro.getName().equals(dataTag.getName())).findAny();
		if (onlyCallable) {
			processor = processor.filter(DataTagProcessor::isCallable);
		}
		if (processor.isPresent()) {
			return Optional.of(processor.get().getData(dataTag.getAttrs()));
		}
		return Optional.empty();
	}

	@Override
	public Optional<Template> queryTemplate(String templateName) {
		return Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
			for (TemplateProcessor processor : templateProcessors) {
				if (processor.canProcess(templateName)) {
					return Optional.ofNullable(processor.getTemplate(templateName));
				}
			}
			throw new SystemException("无法处理模板名：" + templateName + "对应的模板");
		});
	}

	@Override
	public List<ExportPage> exportPage(Integer spaceId) throws LogicException {
		DefaultTransactionDefinition td = new DefaultTransactionDefinition();
		td.setReadOnly(true);
		TransactionStatus status = platformTransactionManager.getTransaction(td);
		try {
			Space space = spaceCache.checkSpace(spaceId);
			List<ExportPage> exportPages = new ArrayList<>();
			// User
			for (Page page : pageDao.selectBySpace(space)) {
				exportPages.add(export(page));
			}
			return exportPages;
		} catch (LogicException | RuntimeException | Error e) {
			status.setRollbackOnly();
			throw e;
		} finally {
			platformTransactionManager.commit(status);
		}
	}

	@Override
	public synchronized void compareTemplate(String templateName, Template template, Consumer<Boolean> consumer) {
		Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
			Optional<Template> current = queryTemplate(templateName);
			boolean equalsTo = current.isPresent() && template != null && current.get().equalsTo(template);
			consumer.accept(equalsTo);
		});
	}

	@Override
	public synchronized List<ImportRecord> importPage(Integer spaceId, List<ExportPage> exportPages) {
		if (CollectionUtils.isEmpty(exportPages)) {
			return new ArrayList<>();
		}
		// 如果导入的空间不存在，直接返回
		Space space;
		try {
			space = spaceCache.checkSpace(spaceId);
		} catch (LogicException e) {
			return Arrays.asList(new ImportRecord(false, e.getLogicMessage()));
		}
		// 开启一个新的串行化事务
		DefaultTransactionDefinition td = new DefaultTransactionDefinition();
		td.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
		td.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		TransactionStatus ts = platformTransactionManager.getTransaction(td);
		List<ImportRecord> records = new ArrayList<>();
		// 设置一个新的页面mapping辅助类
		// 此时锁住RequestMappingHandlerMapping
		// 事务结束后自动解锁
		PageRequestMappingRegisterHelper helper = new PageRequestMappingRegisterHelper();
		try {
			// 用于导入结束后清空缓存
			Set<String> pageEvitKeySet = new HashSet<>();
			Set<String> fragmentEvitKeySet = new HashSet<>();

			// 从导入页面中获取页面
			List<Page> pages = exportPages.stream().map(ExportPage::getPage).collect(Collectors.toList());
			// 从导入页面中获取fragments，按照name去重
			List<Fragment> fragments = exportPages.stream().flatMap(ep -> ep.getFragments().stream()).distinct()
					.collect(Collectors.toList());

			for (Page page : pages) {
				try {
					// 确保page的alias中不包含space信息
					enablePageAliasNotContainsSpace(page.getAlias());
				} catch (LogicException e) {
					records.add(new ImportRecord(true, e.getLogicMessage()));
					ts.setRollbackOnly();
					return records;
				}
				// 设置空间，用于获取templateName
				page.setSpace(space);
				String templateName = page.getTemplateName();
				// 利用templateName查询当前是否已经存在页面
				Optional<Page> optional = queryPageWithTemplateName(templateName);
				// 如果不存在，插入一个自定义页面
				if (!optional.isPresent()) {
					page.setCreateDate(Timestamp.valueOf(LocalDateTime.now()));
					page.setDescription("");
					page.setAllowComment(false);

					pageDao.insert(page);

					// 尝试注册mapping，如果此时存在了其他该路径的mapping(PathTemplate
					// mapping)那么无法注册成功
					try {
						helper.registerPage(page);
					} catch (LogicException ex) {
						records.add(new ImportRecord(true, ex.getLogicMessage()));
						ts.setRollbackOnly();
						return records;
					}

					records.add(new ImportRecord(true, new Message("import.insert.page.success",
							"插入页面" + page.getName() + "[" + page.getAlias() + "]成功", page.getName(), page.getAlias())));
					pageEvitKeySet.add(templateName);
				} else {
					// 可能需要更新页面
					Page current = optional.get();
					// 如果页面内容发生了改变，此时需要更新页面
					if (!current.getTpl().equals(page.getTpl())) {
						current.setTpl(page.getTpl());
						pageDao.update(current);
						helper.unregisterPage(current);
						try {
							helper.registerPage(current);
						} catch (LogicException ex) {
							records.add(new ImportRecord(true, ex.getLogicMessage()));
							ts.setRollbackOnly();
							return records;
						}
						records.add(new ImportRecord(true,
								new Message("import.update.page.success",
										"更新页面" + page.getName() + "[" + page.getAlias() + "]成功", page.getName(),
										page.getAlias())));

						pageEvitKeySet.add(templateName);
					} else {
						records.add(new ImportRecord(true, new Message("import.page.nochange",

								"页面" + page.getName() + "[" + page.getAlias() + "]内容没有发生变化，无需更新", page.getName(),
								page.getAlias())));
					}
				}
			}

			for (Fragment fragment : fragments) {
				String fragmentName = fragment.getName();
				fragment.setSpace(space);
				// 查询当前的fragment
				Optional<Fragment> optionalFragment = queryFragmentWithTemplateName(fragment.getTemplateName());
				// 如果当前没有fragment，插入一个space级别的fragment
				if (!optionalFragment.isPresent()) {
					insertFragmentWhenImport(fragment, records);
					fragmentEvitKeySet.add(fragmentName);
				} else {
					Fragment currentFragment = optionalFragment.get();
					// 模版内容没有发生改变，无需变动
					if (currentFragment.getTpl().equals(fragment.getTpl())) {
						records.add(new ImportRecord(true, new Message("import.fragment.nochange",
								"模板片段" + fragmentName + "内容没有发生变化，无需更新", fragmentName)));
					} else {
						// 如果是内置模板片段，插入新模板片段
						if (!currentFragment.hasId()) {
							insertFragmentWhenImport(fragment, records);
						} else {
							// 如果是global的，则插入space级别的
							if (currentFragment.isGlobal()) {
								insertFragmentWhenImport(fragment, records);
							} else {
								currentFragment.setTpl(fragment.getTpl());
								fragmentDao.update(currentFragment);
								records.add(new ImportRecord(true, new Message("import.update.fragment.success",
										"模板片段" + fragmentName + "更新成功", fragmentName)));
							}
						}
						fragmentEvitKeySet.add(fragmentName);
					}
				}
			}
			// 清空template 缓存
			evitPageCache(pageEvitKeySet.stream().toArray(i -> new String[i]));
			evitFragmentCache(fragmentEvitKeySet.stream().toArray(i -> new String[i]));
			return records;
		} catch (Throwable e) {
			LOGGER.error(e.getMessage(), e);
			ts.setRollbackOnly();
			records.add(new ImportRecord(true, Constants.SYSTEM_ERROR));
			return records;
		} finally {
			platformTransactionManager.commit(ts);
		}
	}

	@Override
	public void registerPreview(String path, Template template) throws LogicException {
		previewManager.registerPreview(path, template);
	}

	@Override
	public void clearPreview() {
		previewManager.clearPreview(true);
	}

	/**
	 * 导入时候插入fragment
	 * 
	 * @param toImport
	 * @param records
	 */
	private void insertFragmentWhenImport(Fragment toImport, List<ImportRecord> records) {
		Fragment fragment = new Fragment();
		fragment.setCreateDate(Timestamp.valueOf(LocalDateTime.now()));
		fragment.setDescription("");
		fragment.setGlobal(false);
		fragment.setName(toImport.getName());
		fragment.setTpl(toImport.getTpl());
		fragment.setSpace(toImport.getSpace());
		fragmentDao.insert(fragment);
		records.add(new ImportRecord(true,
				new Message("import.insert.tpl.success", "模板" + toImport.getName() + "插入成功", toImport.getName())));
	}

	@Override
	public PathTemplateService getPathTemplateService() throws LogicException {
		if (!enablePathTemplate) {
			throw new LogicException("pathTemplate.service.disable", "物理文件模板服务不可用");
		}
		return pathTemplateService;
	}

	/**
	 * 容器重新启动时载入mapping
	 * 
	 * @param evt
	 * @throws Exception
	 */
	@EventListener
	public void handleContextRefreshEvent(ContextRefreshedEvent evt) throws Exception {
		if (evt.getApplicationContext().getParent() != null) {

			Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
				PageRequestMappingRegisterHelper helper = new PageRequestMappingRegisterHelper();
				List<Page> allPage = pageDao.selectAll();
				for (Page page : allPage) {
					try {
						helper.registerPage(page);
					} catch (LogicException e) {
						throw new SystemException(e.getLogicMessage().getCodes()[0]);
					}
				}
			});

			if (enablePathTemplate) {
				pathTemplateService.loadPathTemplateFile("");
			}
		}
	}

	/**
	 * 清空缓存时删除预览模板
	 * 
	 * @param evt
	 */
	@EventListener
	public void handleTemplateEvitEvent(TemplateEvitEvent evt) {
		if (evt.clear()) {
			previewManager.clearPreview(false);
		} else {
			previewManager.remove(evt.getTemplateNames());
		}
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		templateMappingRegister = new TemplateMappingRegister((TemplateRequestMappingHandlerMapping) requestMapping);

		initSystemTemplates();

		if (pathTemplateRoot != null) {
			FileUtils.forceMkdir(pathTemplateRoot);

			pathTemplateService = new PathTemplateServiceImpl();
			LOGGER.debug("开启了物理文件模板服务");
			enablePathTemplate = true;
		}

		// System Template Processor
		this.templateProcessors.add(new TemplateProcessor() {

			@Override
			public Template getTemplate(String templateName) {
				String[] array = templateName.split(Template.SPLITER);
				String path;
				if (array.length == 3) {
					path = array[2];
				} else if (array.length == 2) {
					path = "";
				} else {
					throw new SystemException("无法从" + templateName + "中获取路径");
				}
				Template template = defaultTemplates.get(path);
				if (template != null) {
					template = template.cloneTemplate();
				}
				return template;
			}

			@Override
			public boolean canProcess(String templateName) {
				return SystemTemplate.isSystemTemplate(templateName);
			}
		});

		// Page Template Processor
		this.templateProcessors.add(new TemplateProcessor() {

			@Override
			public Template getTemplate(String templateName) {
				return queryPageWithTemplateName(templateName).orElse(null);
			}

			@Override
			public boolean canProcess(String templateName) {
				return Page.isPageTemplate(templateName);
			}
		});

		// Fragment Template Processor
		this.templateProcessors.add(new TemplateProcessor() {

			@Override
			public Template getTemplate(String templateName) {
				return queryFragmentWithTemplateName(templateName).orElse(null);
			}

			@Override
			public boolean canProcess(String templateName) {
				return Fragment.isFragmentTemplate(templateName);
			}
		});

		// PathTemplate Processor
		if (enablePathTemplate) {
			this.templateProcessors.add(new TemplateProcessor() {

				@Override
				public Template getTemplate(String templateName) {
					return pathTemplateService.getPathTemplate(templateName).orElse(null);
				}

				@Override
				public boolean canProcess(String templateName) {
					return PathTemplate.isPathTemplate(templateName);
				}
			});
		}

		// 预览文件模板服务
		this.previewManager = new PreviewManager();

		this.templateProcessors.add(new TemplateProcessor() {

			@Override
			public Template getTemplate(String templateName) {
				return previewManager.getTemplate(templateName).orElse(null);
			}

			@Override
			public boolean canProcess(String templateName) {
				return Template.isPreviewTemplate(templateName);
			}
		});

		// add space delete event listener
		AbstractApplicationContext appContext = (AbstractApplicationContext) applicationContext.getParent();
		appContext.addApplicationListener(new SpaceDeleteEventListener());
	}

	/**
	 * 初始化系统默认模板，这些模板都能被删除
	 * 
	 * @throws Exception
	 */
	private void initSystemTemplates() throws Exception {
		defaultTemplates = new HashMap<>();
		// 博客主页
		defaultTemplates.put("", new SystemTemplate("", "resources/page/PAGE_INDEX.html"));
		// 博客登录页
		defaultTemplates.put("login", new SystemTemplate("login", "resources/page/LOGIN.html"));
		// 各个空间的主页
		defaultTemplates.put("space/{alias}", new SystemTemplate("space/{alias}", "resources/page/PAGE_INDEX.html"));
		// 主空间解锁页
		defaultTemplates.put("unlock", new SystemTemplate("unlock", "resources/page/PAGE_LOCK.html"));
		// 各个空间解锁页面
		defaultTemplates.put("space/{alias}/unlock",
				new SystemTemplate("space/{alias}/unlock", "resources/page/PAGE_LOCK.html"));
		// 主空间错误显示页面
		defaultTemplates.put("error", new SystemTemplate("error", "resources/page/PAGE_ERROR.html"));
		// 各个空间错误显示页面
		defaultTemplates.put("space/{alias}/error",
				new SystemTemplate("space/{alias}/error", "resources/page/PAGE_ERROR.html"));
		// 各个空间文章详情页面
		defaultTemplates.put("space/{alias}/article/{idOrAlias}",
				new SystemTemplate("space/{alias}/article/{idOrAlias}", "resources/page/PAGE_ARTICLE_DETAIL.html"));
		// 文章归档页面
		defaultTemplates.put("archives", new SystemTemplate("archives", "resources/page/PAGE_ARCHIVES.html"));
		defaultTemplates.put("space/{alias}/archives",
				new SystemTemplate("space/{alias}/archives", "resources/page/PAGE_ARCHIVES.html"));

		long stamp = templateMappingRegister.lockWrite();
		try {
			for (Map.Entry<String, SystemTemplate> it : defaultTemplates.entrySet()) {
				this.templateMappingRegister.registerTemplateMapping(it.getValue().getTemplateName(), it.getKey());
			}
		} finally {
			templateMappingRegister.unlockWrite(stamp);
		}
	}

	private void evitPageCache(String... templateNames) {
		if (templateNames != null && templateNames.length > 0) {
			Transactions.afterCommit(
					() -> this.applicationEventPublisher.publishEvent(new TemplateEvitEvent(this, templateNames)));
		}
	}

	private void evitPageCache(Page... pages) {
		evitPageCache(Arrays.stream(pages).map(Page::getTemplateName).toArray(i -> new String[i]));
	}

	private void evitFragmentCache(String... names) {
		Transactions.afterCommit(() -> {
			if (names == null || names.length == 0) {
				return;
			}

			// fragment比较特殊，它是按照名称来区分的，尝试fragment的缓存时
			// 需要删除各个空间中存在该名称的fragment缓存
			List<Space> spaces = spaceCache.getSpaces(true);
			Set<String> templateNames = new HashSet<>();
			for (String name : names) {
				templateNames.add(Fragment.getTemplateName(name, null));
				for (Space space : spaces) {
					templateNames.add(Fragment.getTemplateName(name, space));
				}
			}
			this.applicationEventPublisher
					.publishEvent(new TemplateEvitEvent(this, templateNames.stream().toArray(i -> new String[i])));
		});
	}

	private Optional<Fragment> queryFragmentWithTemplateName(String templateName) {
		String[] array = templateName.split(Template.SPLITER);
		String name;
		Space space = null;
		if (array.length == 3) {
			name = array[2];
		} else if (array.length == 4) {
			name = array[2];
			space = new Space(Integer.parseInt(array[3]));
		} else {
			throw new SystemException(templateName + "无法转化为Fragment");
		}
		Fragment fragment = fragmentDao.selectBySpaceAndName(space, name);
		if (fragment == null) { // 查找全局
			fragment = fragmentDao.selectGlobalByName(name);
		}

		if (fragment == null) {
			// 查找内置模板片段
			// 为了防止默认模板片段被修改，这里首先进行clone
			fragment = fragments.stream().filter(fb -> fb.getName().equals(name)).findAny().map(Fragment::new)
					.orElse(null);
		}
		return Optional.ofNullable(fragment);
	}

	private Optional<Page> queryPageWithTemplateName(String templateName) {
		Page page = null;
		String[] array = templateName.split(Template.SPLITER);
		if (array.length == 2) {
			page = pageDao.selectBySpaceAndAlias(null, "");
		} else if (array.length == 3) {
			page = pageDao.selectBySpaceAndAlias(null, array[2]);
		} else if (array.length == 4) {
			page = pageDao.selectBySpaceAndAlias(new Space(Integer.parseInt(array[3])), array[2]);
		} else {
			throw new SystemException(templateName + "无法转化为用户自定义页面");
		}
		return Optional.ofNullable(page);
	}

	private ExportPage export(Page page) {
		ExportPage exportPage = new ExportPage();
		exportPage.setPage(page.toExportPage());
		Map<String, Fragment> fragmentMap = new HashMap<>();
		fillMap(fragmentMap, page.getSpace(), page.getTpl());
		for (Fragment fragment : fragmentMap.values()) {
			if (fragment != null) {
				exportPage.add(fragment.toExportFragment());
			}
		}
		fragmentMap.clear();
		return exportPage;
	}

	private void fillMap(Map<String, Fragment> fragmentMap, Space space, String tpl) {
		Map<String, Fragment> fragmentMap2 = new HashMap<>();
		Document document = Jsoup.parse(tpl);
		Elements elements = document.getElementsByTag("fragment");
		for (Element element : elements) {
			String name = element.attr("name");
			if (fragmentMap.containsKey(name)) {
				continue;
			}
			Optional<Fragment> optional = queryFragmentWithTemplateName(Fragment.getTemplateName(name, space));
			fragmentMap.put(name, optional.orElse(null));
			if (optional.isPresent()) {
				fragmentMap2.put(name, optional.get());
			}
		}
		for (Map.Entry<String, Fragment> fragmentIterator : fragmentMap2.entrySet()) {
			Fragment value = fragmentIterator.getValue();
			fillMap(fragmentMap, space, value.getTpl());
		}
		fragmentMap2.clear();
	}

	private final class PathTemplateServiceImpl implements PathTemplateService {

		private static final String PATH_TEMPLATE_SUFFIX = ".html";
		private static final String PATH_TEMPLATE_REG_SUFFIX = ".reg.html";
		private static final String PUBLIC_FRAGMENT_TEMPLATE_SUFFIX = ".pub.html";
		private static final String PREVIEW_SUFFIX = ".preview.html";

		private final Map<String, PathTemplate> pathTemplates = new ConcurrentHashMap<>();

		private final Comparator<PathTemplate> comparator = new PathTemplateComparator();

		@Override
		public List<PathTemplate> queryPathTemplates(String str) {
			Predicate<PathTemplate> filter = Validators.isEmptyOrNull(str, true) ? pathTemplate -> true
					: new ContainsFilter(str);
			List<PathTemplate> templates = new ArrayList<>(pathTemplates.values().parallelStream().filter(filter)
					.sorted(comparator).collect(Collectors.toList()));

			return Collections.unmodifiableList(templates);
		}

		@Override
		public PathTemplate registerPreview(PathTemplateBean bean) throws LogicException {
			String cleanPath = FileUtils.cleanPath(bean.getPath());
			// 查找preview
			Path previewPath = pathTemplateRoot.resolve(cleanPath + PREVIEW_SUFFIX);
			if (FileUtils.exists(previewPath)) {
				if (!FileUtils.isSub(previewPath, pathTemplateRoot)) {
					throw new LogicException("pathTemplate.preview.notInRoot", "文件不在模板主目录中");
				}
				if (!FileUtils.isRegularFile(previewPath)) {
					throw new LogicException("pathTemplate.preview.notFile", "文件夹不能被预览");
				}
			}
			FileUtils.forceMkdir(previewPath.getParent());
			try {
				synchronized (this) {
					Files.copy(new ByteArrayInputStream(bean.getTpl().getBytes(Constants.CHARSET)), previewPath,
							StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				LOGGER.debug(e.getMessage(), e);
				throw new LogicException("pathTemplate.preview.ioError", "写入预览文件异常");
			}

			PathTemplate preview = new PathTemplate(previewPath, true, cleanPath, false);
			previewManager.registerPreview(cleanPath, preview);
			return preview;
		}

		@Override
		public Optional<PathTemplate> getPathTemplate(String templateName) {
			if (!PathTemplate.isPathTemplate(templateName)) {
				return Optional.empty();
			}
			String path;
			String spaceAlias = null;
			// Template%Path%path
			// Template%Path%
			// Template%Path%%space
			// Template%Path%path%space
			String[] array = templateName.split(Template.SPLITER);
			if (array.length == 2) {
				path = "";
			} else if (array.length == 3) {
				path = array[2];
			} else if (array.length == 4) {
				path = array[2];
				spaceAlias = array[3];
			} else {
				throw new SystemException("无法从" + templateName + "中获取路径");
			}

			PathTemplate template = null;
			path = FileUtils.cleanPath(path);
			// 从空间中寻找
			if (spaceAlias == null) {
				// 如果是默认空间，无法加载其他空间的PathTemplate
				if (Webs.getSpaceFromPath(path) == null) {
					template = pathTemplates.get(path);
				}
			} else {
				template = pathTemplates.get("space/" + spaceAlias + (path.isEmpty() ? "" : "/" + path));
			}
			if (template == null) {
				// 再次寻找 public pub
				template = pathTemplates.get(path);
				// 如果存在，不是public，认为没有找到
				if (template != null && !template.isPub()) {
					template = null;
				}
			}
			if (template != null) {
				template = new PathTemplate(template);
			}
			return Optional.ofNullable(template);
		}

		@Override
		public PathTemplate build(PathTemplateBean templateBean) throws LogicException {
			// 根据相对路径查找文件
			String resolvePath = FileUtils
					.cleanPath(templateBean.getPath() + (templateBean.isPub() ? PUBLIC_FRAGMENT_TEMPLATE_SUFFIX
							: templateBean.isRegistrable() ? PATH_TEMPLATE_REG_SUFFIX : PATH_TEMPLATE_SUFFIX));

			Path path = pathTemplateRoot.resolve(resolvePath);

			if (!FileUtils.isSub(path, pathTemplateRoot)) {
				throw new LogicException("pathTemplate.build.notInRoot", "文件不在模板主目录中");
			}

			boolean exists = FileUtils.exists(path);// 判断原路径是否已经存在，如果不存在并且保存失败，删除path
			FileUtils.forceMkdir(path.getParent());
			try {
				synchronized (this) {
					Files.copy(new ByteArrayInputStream(templateBean.getTpl().getBytes(Constants.CHARSET)), path,
							StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				if (!exists) {
					FileUtils.deleteQuietly(path);
				}
				LOGGER.debug(e.getMessage(), e);
				throw new LogicException("pathTemplate.write.ioError", "写入文件异常");
			}

			long stamp = templateMappingRegister.lockWrite();
			try {
				PathTemplateLoadRecord record = loadPathTemplateFile(path);
				if (!record.isSuccess()) {
					if (!exists) {
						FileUtils.deleteQuietly(path);
					}
					throw new LogicException(record.getMessage());
				}
				return this.getPathTemplate(PathTemplate.getTemplateName(FileUtils.cleanPath(templateBean.getPath())))
						.orElseThrow(() -> new SystemException("创建|更新成功后无法找到对应的物理文件模板"));
			} finally {
				templateMappingRegister.unlockWrite(stamp);
			}
		}

		@Override
		public void deletePathTemplate(String path) throws LogicException {
			PathTemplate template = this.pathTemplates.get(FileUtils.cleanPath(path));
			if (template != null) {
				Path associate = template.getAssociate();
				if (FileUtils.deleteQuietly(associate)) {
					Path parent = associate.getParent();
					this.loadPathTemplateFile(pathTemplateRoot.relativize(parent).toString());
				} else {
					throw new LogicException("pathTemplate.delete.fail", "删除文件失败");
				}
			} else {
				throw new LogicException("pathTemplate.path.notExists", "路径不存在");
			}
		}

		@Override
		public List<PathTemplateLoadRecord> loadPathTemplateFile(String path) {
			// 锁住Outer class，为了保证queryTemplate的时候没有其他写线程修改数据
			synchronized (TemplateServiceImpl.this) {
				// 获取RequestMapping的锁
				long stamp = templateMappingRegister.lockWrite();
				try {
					// 定位需要载入的目录
					Path loadPath = pathTemplateRoot.resolve(FileUtils.cleanPath(path));
					if (!FileUtils.exists(loadPath)) {
						LOGGER.debug("文件{}不存在", loadPath);
						return Arrays.asList(new PathTemplateLoadRecord(null, false,
								new Message("pathTemplate.path.notExists", "路径不存在")));
					}
					if (!FileUtils.isSub(loadPath, pathTemplateRoot)) {
						LOGGER.debug("文件{}不存在于模板主目录中", loadPath);
						return Arrays.asList(new PathTemplateLoadRecord(null, false,
								new Message("pathTemplate.load.path.notInRoot", "文件不存在于模板主目录中")));
					}

					List<PathTemplateLoadRecord> records = new ArrayList<>();

					if (FileUtils.isRegularFile(loadPath)) {
						if (!isPreview(loadPath)) {
							records.add(this.loadPathTemplateFile(loadPath));
						}
					} else {
						// 查找被删除(期望解除注册)的文件
						for (PathTemplate pathTemplate : pathTemplates.values()) {
							Path associate = pathTemplate.getAssociate();
							if (FileUtils.isSub(associate, loadPath) && !FileUtils.exists(associate)) {
								String relativePath = getPathTemplatePath(associate);
								if (pathTemplate.isRegistrable()) {
									// 如果是可注册的，删除mapping
									templateMappingRegister.unregisterTemplateMapping(relativePath);
								}
								pathTemplates.remove(relativePath);
								applicationEventPublisher
										.publishEvent(new TemplateEvitEvent(this, pathTemplate.getTemplateName()));
								LOGGER.debug("文件{}不存在，删除", associate);
								records.add(new PathTemplateLoadRecord(relativePath, true,
										new Message("pathTemplate.load.removeSuccess", "删除成功")));
							}
						}

						try {
							// 寻找文件夹下所有符合条件的路径
							records.addAll(
									Files.walk(loadPath, PageValidator.MAX_ALIAS_DEPTH).filter(Files::isRegularFile)
											.filter(this::isPathTemplate).filter(p -> !isPreview(p))
											.map(this::loadPathTemplateFile).collect(Collectors.toList()));
						} catch (IOException e) {
							throw new SystemException(e.getMessage(), e);
						}
					}

					return records;
				} finally {
					templateMappingRegister.unlockWrite(stamp);
				}
			}
		}

		// 载入一个文件
		private PathTemplateLoadRecord loadPathTemplateFile(Path file) {
			String relativePath = getPathTemplatePath(file);

			if (pathTemplates.containsKey(relativePath)) {
				PathTemplate exists = pathTemplates.get(relativePath);
				if (file.getFileName().toString().equals(exists.getAssociate().getFileName().toString())) {
					// 如果是同一个文件
					// 刷新缓存
					String templateName = exists.getTemplateName();
					applicationEventPublisher.publishEvent(new TemplateEvitEvent(this, templateName));
					// 如果是可注册的，尝试注册，因为当容器重启时路径会丢失
					if (isRegPathTemplate(file)) {
						try {
							templateMappingRegister.registerTemplateMapping(templateName, relativePath);
						} catch (LogicException e) {
							// 忽略这个异常，已经被注册了
						}
					}
					LOGGER.debug("文件:{}载入成功", file);
					return new PathTemplateLoadRecord(relativePath, true,
							new Message("pathTemplate.load.loadSuccess", "载入成功"));
				} else {
					// 不是同一个文件，却有相同的relativePath
					// /dir/a.reg.html
					// /dir/a.html
					LOGGER.debug("文件:{}已经存在", file);
					return new PathTemplateLoadRecord(relativePath, false,
							new Message("pathTemplate.load.exists", "已经存在文件对应该路径"));
				}
			}

			if (!FileUtils.exists(file)) {
				LOGGER.debug("文件:{}不存在", file);
				return new PathTemplateLoadRecord(relativePath, false,
						new Message("pathTemplate.path.notExists", "路径不存在"));
			}
			if (!Files.isReadable(file)) {
				LOGGER.debug("文件:{}不可读", file);
				return new PathTemplateLoadRecord(relativePath, false,
						new Message("pathTemplate.load.path.notReadable", "路径不可读"));
			}
			if (isRegPathTemplate(file)) {
				// 验证注册路径的正确性
				Errors errors = new MapBindingResult(new HashMap<>(), "pathTemplate");
				relativePath = PageValidator.validateAlias(relativePath, errors);
				if (errors.hasErrors()) {
					ObjectError first = errors.getAllErrors().get(0);
					LOGGER.debug("文件:{}，对应的映射路径:{},校验失败", file, relativePath);
					return new PathTemplateLoadRecord(relativePath, false,
							new Message(first.getCode(), first.getDefaultMessage(), first.getArguments()));
				}

				PathTemplate pathTemplate = new PathTemplate(file, true, relativePath, false);
				String templateName = pathTemplate.getTemplateName();
				try {
					templateMappingRegister.registerTemplateMapping(templateName, relativePath);
					LOGGER.debug("文件:{}对应的映射路径:{}，注册成功", file, relativePath);
					applicationEventPublisher.publishEvent(new TemplateEvitEvent(this, templateName));
				} catch (LogicException e) {
					LOGGER.debug("文件:{}对应的映射路径:{}，注册失败，路径已经存在", file, relativePath);
					return new PathTemplateLoadRecord(relativePath, false, e.getLogicMessage());
				}

				pathTemplates.put(relativePath, pathTemplate);
			} else {
				// 不是可注册的
				// fragment
				// 如果是全局fragment
				boolean isPublic = isPublicFragment(file);
				if (isPublic) {
					// 全局fragment不能在space/x/文件夹下
					if (Webs.getSpaceFromPath(relativePath) != null) {
						LOGGER.debug("全局fragment:{}不能在space/*/**文件夹下", relativePath);
						return new PathTemplateLoadRecord(relativePath, false,
								new Message("pathTemplate.load.path.publicFragmentInSpace",
										"路径:" + relativePath + "被标记为全局fragment，该路径不能位于space文件夹下", relativePath));
					}
				}

				// 校验fragment name
				String name = FileUtils.getNameWithoutExtension(relativePath);
				if (!name.matches(FragmentValidator.NAME_PATTERN)) {
					return new PathTemplateLoadRecord(relativePath, false,
							new Message("pathTemplate.load.fragment.invalidName", "无效的名称:" + name, name));
				}
				PathTemplate pathTemplate = new PathTemplate(file, false, relativePath, isPublic);

				String templateName = pathTemplate.getTemplateName();
				applicationEventPublisher.publishEvent(new TemplateEvitEvent(this, templateName));

				pathTemplates.put(relativePath, pathTemplate);
			}
			LOGGER.debug("文件{}载入成功", file);
			return new PathTemplateLoadRecord(relativePath, true, new Message("pathTemplate.load.loadSuccess", "载入成功"));
		}

		private boolean isPublicFragment(Path path) {
			return path.toString().endsWith(PUBLIC_FRAGMENT_TEMPLATE_SUFFIX);
		}

		private boolean isRegPathTemplate(Path path) {
			return path.toString().endsWith(PATH_TEMPLATE_REG_SUFFIX);
		}

		private boolean isPreview(Path path) {
			return path.toString().endsWith(PREVIEW_SUFFIX);
		}

		private boolean isPathTemplate(Path path) {
			return path.toString().endsWith(PATH_TEMPLATE_SUFFIX);
		}

		private String getPathTemplatePath(Path path) {
			String relativizePath = pathTemplateRoot.relativize(path).toString();
			if (isRegPathTemplate(path)) {
				relativizePath = relativizePath.substring(0,
						relativizePath.length() - PATH_TEMPLATE_REG_SUFFIX.length());
			} else if (isPublicFragment(path)) {
				relativizePath = relativizePath.substring(0,
						relativizePath.length() - PUBLIC_FRAGMENT_TEMPLATE_SUFFIX.length());
			} else {
				relativizePath = relativizePath.substring(0, relativizePath.length() - PATH_TEMPLATE_SUFFIX.length());
			}
			return FileUtils.cleanPath(relativizePath);
		}

		/**
		 * 删除预览文件
		 */
		private void deleteAllPreview() {
			FileUtils.deleteQuietly(pathTemplateRoot, this::isPreview);
		}

		private final class ContainsFilter implements Predicate<PathTemplate> {

			private final String str;

			public ContainsFilter(String str) {
				super();
				this.str = str;
			}

			@Override
			public boolean test(PathTemplate t) {
				return t.getRelativePath().contains(str);
			}
		}

		private final class PathTemplateComparator implements Comparator<PathTemplate> {

			@Override
			public int compare(PathTemplate o1, PathTemplate o2) {
				Integer order1 = getOrder(o1.getAssociate());
				Integer order2 = getOrder(o2.getAssociate());
				if (order1.equals(order2)) {
					return o1.getRelativePath().compareTo(o2.getRelativePath());
				}
				return order1.compareTo(order2);
			}

			private Integer getOrder(Path path) {
				if (isRegPathTemplate(path)) {
					return 1;
				}
				if (isPublicFragment(path)) {
					return 3;
				}
				if (isPathTemplate(path)) {
					return 2;
				}
				throw new SystemException("仅支持.reg.html,.html,.pub.html的排序");
			}
		}
	}

	private final class PreviewManager {

		private Map<String, Template> previewMap = new HashMap<>();
		private ReadWriteLock lock = new ReentrantReadWriteLock();

		public void registerPreview(String path, Template template) throws LogicException {
			lock.writeLock().lock();
			try {
				long stamp = templateMappingRegister.lockWrite();
				try {
					String cleanPath = FileUtils.cleanPath(path);
					templateMappingRegister.registerPreviewMapping(cleanPath);
					previewMap.put(cleanPath, template);
				} finally {
					templateMappingRegister.unlockWrite(stamp);
				}
			} finally {
				lock.writeLock().unlock();
			}
		}

		/**
		 * 清空预览页面
		 * 
		 * @param unmapping
		 *            是否解除RequestMapping
		 *            <p>
		 *            因为在{@code TemplateEvitEvent}的事件处理中同样会调用这个方法，此时unmapping可能导致死锁，因为{@code StampedLock}是不可重入的
		 *            </p>
		 * 
		 */
		public void clearPreview(boolean unmapping) {
			lock.writeLock().lock();
			try {
				previewMap.clear();
				if (pathTemplateService != null) {
					pathTemplateService.deleteAllPreview();
				}
				if (unmapping) {
					long stamp = templateMappingRegister.lockWrite();
					try {
						templateMappingRegister.unregisterPreviewMappings();
					} finally {
						templateMappingRegister.unlockWrite(stamp);
					}
				}
			} finally {
				lock.writeLock().unlock();
			}
		}

		public Optional<Template> getTemplate(String templateName) {
			if (!Template.isPreviewTemplate(templateName)) {
				return Optional.empty();
			}
			lock.readLock().lock();
			try {
				if (previewMap.isEmpty()) {
					return Optional.empty();
				}
				String path = templateName.substring(Template.TEMPLATE_PREVIEW_PREFIX.length());
				return Optional.ofNullable(previewMap.get(path));
			} finally {
				lock.readLock().unlock();
			}
		}

		/**
		 * <p>
		 * 获取最匹配的preview路径<br>
		 * 如果注册了space/test/{test}路径，那么访问space/test/apk将会匹配到该路径<br>
		 * 但是如果space/test/apk已经存在，仍然不会进入space/test/apk这个mapping，而是进入space/test/{test}这个mapping<br>
		 * 为了防止这种情况的发生，将当前被匹配到的path用于比对，如果preview中的path级别比当前path高，则用preview中的，否则用当前路径的<br>
		 * </p>
		 * 
		 * @param currentPath
		 * @param templateName
		 * @return
		 */
		Optional<String> getBestMatchTemplateName(String currentPath, String templateName) {
			if (!Template.isPreviewTemplate(templateName)) {
				return Optional.empty();
			}
			lock.readLock().lock();
			try {
				if (previewMap.isEmpty()) {
					return Optional.empty();
				}
				// 如果直接匹配成功
				String path = templateName.substring(Template.TEMPLATE_PREVIEW_PREFIX.length());
				Template template = previewMap.get(path);
				if (template == null) {
					// 没有直接匹配的path，只能遍历全部path，寻找最合适的path
					Set<String> pathSet = new HashSet<>(previewMap.keySet());
					if (currentPath != null) {
						pathSet.add(currentPath);
					}
					PatternsRequestCondition condition = new PatternsRequestCondition(
							pathSet.toArray(new String[pathSet.size()]));
					List<String> matches = condition.getMatchingPatterns("/" + path);
					if (!matches.isEmpty()) {
						String bestMatch = matches.get(0).substring(1);
						template = previewMap.get(bestMatch);
						if (template != null) {
							return Optional.of(Template.TEMPLATE_PREVIEW_PREFIX + bestMatch);
						}
					}
					return Optional.empty();
				} else {
					return Optional.of(Template.TEMPLATE_PREVIEW_PREFIX + path);
				}
			} finally {
				lock.readLock().unlock();
			}
		}

		void remove(String... templateNames) {
			if (templateNames == null || templateNames.length == 0) {
				return;
			}
			lock.writeLock().lock();
			try {
				previewMap.entrySet().removeIf(it -> {
					String previewTemplateName = it.getValue().getTemplateName();
					for (String templateName : templateNames) {
						if (previewTemplateName.equals(templateName)) {
							return true;
						}
					}
					return false;
				});
			} finally {
				lock.writeLock().unlock();
			}
		}
	}

	private interface TemplateProcessor {
		/**
		 * 是否能够处理该模板
		 * 
		 * @param templateName
		 * @return
		 */
		boolean canProcess(String templateName);

		/**
		 * 根据模板名查询模板
		 * 
		 * @param templateName
		 *            模板名
		 * @return 模板，如果不存在，返回null
		 */
		Template getTemplate(String template);
	}

	/**
	 * 用来注册template mapping
	 * <p>
	 * <b>这个类的所有方法都不是线程安全的，必须通过lockWrite和unlockWrite来保证线程安全</b>
	 * </p>
	 * <p>
	 * <b>lockWrite和unlockWrite同时会阻塞前端页面的渲染</b>
	 * </p>
	 * 
	 * @see #lockWrite()
	 * @see #unlockWrite(long)
	 * @author mhlx
	 *
	 */
	private final class TemplateMappingRegister {

		private final Method method;
		private final PatternsRequestCondition condition;
		private final Comparator<String> comparator = new AntPathMatcher().getPatternComparator(null);
		private final List<RequestMappingInfo> previewMappings = new ArrayList<>();
		private final MappingRegistry mappingRegistry;

		TemplateMappingRegister(TemplateRequestMappingHandlerMapping requestMappingHandlerMapping) throws Exception {
			super();
			this.mappingRegistry = requestMappingHandlerMapping.getPublicMappingRegistry();
			method = TemplateController.class.getMethod("handleRequest", HttpServletRequest.class);
			Set<String> patternSet = new HashSet<>();

			// 遍历所有的系统默认路径
			for (Map.Entry<RequestMappingInfo, HandlerMethod> it : mappingRegistry.getMappings().entrySet()) {
				RequestMappingInfo info = it.getKey();
				HandlerMethod method = it.getValue();
				PatternsRequestCondition condition = info.getPatternsCondition();
				if (!(method.getBean() instanceof TemplateController)) {
					RequestMethodsRequestCondition methodsRequestCondition = info.getMethodsCondition();
					if (methodsRequestCondition.isEmpty()
							|| methodsRequestCondition.getMethods().contains(RequestMethod.GET)) {
						patternSet.addAll(condition.getPatterns());
					}
				}
			}
			condition = new PatternsRequestCondition(patternSet.toArray(new String[patternSet.size()]));
		}

		/**
		 * 写锁
		 * 
		 * @return stamp
		 */
		public long lockWrite() {
			return mappingRegistry.getLock().writeLock();
		}

		/**
		 * 解除写锁
		 * 
		 * @param stamp
		 */
		public void unlockWrite(long stamp) {
			this.mappingRegistry.getLock().unlockWrite(stamp);
		}

		/**
		 * 注册预览mapping
		 * 
		 * @param templateName
		 *            模板名
		 * @param path
		 *            路径
		 * @return 如果注册了一个新的RequestMappingInfo用于预览，那么返回这个mapping。否则返回null
		 */
		public void registerPreviewMapping(String previewPath) throws LogicException {
			String path = FileUtils.cleanPath(previewPath);
			// 判断是否是系统保留路径
			if (isKeyPath(path)) {
				throw new LogicException("requestMapping.preview.keyPath", "路径" + path + "是系统保留路径，无法被预览");
			}
			// 查找是否已经存在可以被映射的RequestMapping
			// 此时应该遍历查找
			// 因为space/{alias}和space/test是两个不同的RequestMapping
			// 但是space/{alias}可以映射到space/test
			String lookupPath = "/" + path;
			boolean hasPathVariable = StringUtils.substringBetween(path, "{", "}") != null;

			for (Map.Entry<RequestMappingInfo, HandlerMethod> it : mappingRegistry.getMappings().entrySet()) {
				HandlerMethod method = it.getValue();
				if (method.getBean() instanceof TemplateController) {
					PatternsRequestCondition condition = it.getKey().getPatternsCondition();
					List<String> bestPatterns = condition.getMatchingPatterns(lookupPath);
					if (!bestPatterns.isEmpty()) {
						// 此时存在匹配的路径
						// 但是可能两者都是PathVariable
						// 例如/{test}和/{testasd}
						String bestPattern = bestPatterns.get(0);
						if (hasPathVariable && comparator.compare(lookupPath, bestPattern) == 0) {
							// 判断是否都是PathVariable，如果两者都是PathVariable,删除前者
							unregisterPreviewMapping(it.getKey());
							break;
						}
						return;
					}
				}
			}
			RequestMappingInfo info = getMethodMapping(path);
			// 再次判断是否存在RequestMappingInfo
			// 因为/{test}和/{testasd}情况下，无法保证能够删除/{test} mapping，因为它可能不是预览mapping
			if (mappingRegistry.getMappings().get(info) == null) {
				mappingRegistry.register(info, new PreviewTemplateController(path), method);
				previewMappings.add(info);
			}
		}

		/**
		 * 解除已经注册的PreviewMapping
		 */
		public void unregisterPreviewMappings() {
			for (RequestMappingInfo mapping : previewMappings) {
				unregisterPreviewMapping(mapping);
			}
		}

		private void unregisterPreviewMapping(RequestMappingInfo mapping) {
			HandlerMethod handlerMethod = mappingRegistry.getMappings().get(mapping);
			if (handlerMethod != null) {
				TemplateController templateController = (TemplateController) handlerMethod.getBean();
				if (Template.isPreviewTemplate(templateController.getTemplateName())) {
					// 此时可以解除
					mappingRegistry.unregister(mapping);
				}
			}
		}

		/**
		 * 注册模板mapping
		 * 
		 * @param templateName
		 *            模板名
		 * @param path
		 *            路径
		 * @throws LogicException
		 *             路径已经存在并且无法清除
		 */
		public void registerTemplateMapping(String templateName, String templatePath) throws LogicException {
			String path = FileUtils.cleanPath(templatePath);
			// 判断是否是系统保留路径
			if (isKeyPath(path)) {
				throw new LogicException("requestMapping.register.keyPath", "路径" + path + "是系统保留路径，无法被注册");
			}
			boolean exists = false;
			RequestMappingInfo info = getMethodMapping(path);
			HandlerMethod handlerMethod = mappingRegistry.getMappings().get(info);
			if (handlerMethod != null) {
				exists = true;
				TemplateController templateController = (TemplateController) handlerMethod.getBean();
				// 如果是系统模板或者预览模板，删除后注册
				if (SystemTemplate.isSystemTemplate(templateController.getTemplateName())
						|| Template.isPreviewTemplate(templateController.getTemplateName())) {
					mappingRegistry.unregister(info);
					exists = false;
				}
			}
			if (exists) {
				throw new LogicException(new Message("requestMapping.register.exists", "路径:" + path + "已经存在", path));
			}
			mappingRegistry.register(info, new _TemplateController(templateName, path), method);
		}

		/**
		 * 解除模板mapping
		 * 
		 * @param path
		 *            模板访问路径
		 */
		public void unregisterTemplateMapping(String templatePath) {
			String path = FileUtils.cleanPath(templatePath);
			if (isKeyPath(path)) {
				return;
			}
			mappingRegistry.unregister(getMethodMapping(path));
			SystemTemplate template = defaultTemplates.get(path);
			// 插入默认系统模板
			if (template != null) {
				String templateName = template.getTemplateName();
				mappingRegistry.register(getMethodMapping(template.getPath()),
						new _TemplateController(templateName, path), method);
			}
		}

		/**
		 * 强制注册模板mapping(先删除后注册)
		 * 
		 * @param templateName
		 *            模板名
		 * @param path
		 *            模板访问路径
		 */
		public void forceRegisterTemplateMapping(String templateName, String templatePath) {
			String path = FileUtils.cleanPath(templatePath);
			if (isKeyPath(path)) {
				return;
			}
			RequestMappingInfo info = getMethodMapping(path);
			mappingRegistry.unregister(info);
			mappingRegistry.register(info, new _TemplateController(templateName, path), method);
		}

		private RequestMappingInfo getMethodMapping(String registPath) {
			return requestMapping.createRequestMappingInfoWithConfig(
					RequestMappingInfo.paths(registPath).methods(RequestMethod.GET));
		}

		private boolean isKeyPath(String path) {
			String lookupPath = "/" + path;
			return !condition.getMatchingPatterns(lookupPath).isEmpty();
		}

		private class PreviewTemplateController extends _TemplateController {

			public PreviewTemplateController(String path) {
				super(Template.TEMPLATE_PREVIEW_PREFIX + path, path);
			}

			@Override
			public TemplateView handleRequest(HttpServletRequest request) {
				if (Environment.isLogin()) {
					return getTemplateView();
				} else {
					throw new TemplateNotFoundException(getTemplateName());
				}
			}
		}
	}

	public class _TemplateController extends TemplateController {

		private _TemplateController(String templateName, String path) {
			super(templateIdGenerator.incrementAndGet(), templateName, path);
		}

		@Override
		public TemplateView handleRequest(HttpServletRequest request) {
			// 如果用户登录状态并且预览服务中存在这个模板，那么返回预览模板名
			if (Environment.isLogin()) {
				String path = FileUtils
						.cleanPath(request.getRequestURI().substring(request.getContextPath().length() + 1));
				String previewTemplateName = Template.TEMPLATE_PREVIEW_PREFIX + path;
				Optional<String> bestTemplateName = previewManager.getBestMatchTemplateName(getPath(),
						previewTemplateName);
				if (bestTemplateName.isPresent()) {
					return new TemplateView(bestTemplateName.get());
				}
			}
			return getTemplateView();
		}
	}

	/**
	 * 用来在一个<b>事务</b>中使mapping和页面保持一致
	 * 
	 * @author mhlx
	 *
	 */
	private final class PageRequestMappingRegisterHelper {

		private List<Runnable> rollBackActions = new ArrayList<>();
		private long stamp;

		public PageRequestMappingRegisterHelper() {
			super();
			if (!TransactionSynchronizationManager.isSynchronizationActive()) {
				throw new SystemException(this.getClass().getName() + " 必须处于一个事务中");
			}
			// 锁住RequestMapping
			this.stamp = templateMappingRegister.lockWrite();

			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {

				@Override
				public void afterCompletion(int status) {
					try {
						if (status == STATUS_ROLLED_BACK) {
							rollback();
						}
					} finally {
						templateMappingRegister.unlockWrite(stamp);
					}
				}

				/**
				 * 这里必须最高的优先级，第一时间解锁
				 */
				@Override
				public int getOrder() {
					return Ordered.HIGHEST_PRECEDENCE;
				}

			});

		}

		void registerPage(Page page) throws LogicException {
			String path = page.getTemplatePath();
			templateMappingRegister.registerTemplateMapping(page.getTemplateName(), path);
			rollBackActions.add(() -> templateMappingRegister.unregisterTemplateMapping(path));
		}

		void unregisterPage(Page page) {
			String path = page.getTemplatePath();
			templateMappingRegister.unregisterTemplateMapping(path);
			rollBackActions
					.add(() -> templateMappingRegister.forceRegisterTemplateMapping(page.getTemplateName(), path));
		}

		private void rollback() {
			if (!rollBackActions.isEmpty()) {
				for (Runnable act : rollBackActions) {
					act.run();
				}
			}
		}
	}

	private final class SpaceDeleteEventListener implements ApplicationListener<SpaceDeleteEvent> {

		@Override
		public void onApplicationEvent(SpaceDeleteEvent event) {
			// 删除所有的fragments
			fragmentDao.deleteBySpace(event.getSpace());
			// 事务结束之后清空所有页面缓存
			Transactions.afterCommit(() -> applicationEventPublisher.publishEvent(new TemplateEvitEvent(this)));
			// 查询所有的页面
			List<Page> pages = pageDao.selectBySpace(event.getSpace());
			if (!pages.isEmpty()) {
				PageRequestMappingRegisterHelper helper = new PageRequestMappingRegisterHelper();
				for (Page page : pages) {
					pageDao.deleteById(page.getId());
					// 解除mapping注册
					helper.unregisterPage(page);
					// 发送事件
					applicationEventPublisher.publishEvent(new PageEvent(this, EventType.DELETE, page));
				}
			}
		}

	}

	public void setProcessors(List<DataTagProcessor<?>> processors) {
		for (DataTagProcessor<?> processor : processors) {
			if (!Validators.isLetterOrNumOrChinese(processor.getName())) {
				throw new SystemException("数据名只能为中英文或者数字");
			}
			if (!Validators.isLetter(processor.getDataName())) {
				throw new SystemException("数据dataName只能为英文字母");
			}
		}
		this.processors = processors;
	}

	/**
	 * 设置系统内置的fragment
	 * <p>
	 * <b>无论是否设置space，这些fragment都是全局的</b>
	 * </p>
	 * 
	 * @param fragments
	 */
	public void setFragments(List<Fragment> fragments) {
		for (Fragment fragment : fragments) {
			// 清除ID，用来判断是否是内置模板片段
			fragment.setId(null);
			this.fragments.add(fragment);
		}
	}

	public void setPathTemplateRoot(String pathTemplateRoot) {
		this.pathTemplateRoot = Paths.get(pathTemplateRoot);
	}
}
