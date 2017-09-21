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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.CollectionUtils;

import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.exception.RuntimeLogicException;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.lock.LockBean;
import me.wangkang.blog.core.lock.LockException;
import me.wangkang.blog.core.lock.LockHelper;
import me.wangkang.blog.core.message.Messages;
import me.wangkang.blog.core.security.AuthencationException;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.util.ExceptionUtils;
import me.wangkang.blog.util.UIUtils;
import me.wangkang.blog.util.Validators;

/**
 * 用来将模板解析成字符串
 * 
 * @author Administrator
 *
 */
public final class TemplateRender implements InitializingBean {

	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private TemplateRenderExecutor templateRenderer;
	@Autowired
	private TemplateExceptionTranslater templateExceptionTranslater;
	@Autowired
	private UrlHelper urlHelper;
	@Autowired
	private Messages messages;

	private Map<String, Object> pros = new HashMap<>();

	public RenderResult render(String templateName, Map<String, Object> model, HttpServletRequest request,
			ReadOnlyResponse response, ParseConfig config) throws TemplateRenderException {
		try {
			return doRender(templateName, model == null ? new HashMap<>() : model, request, response, config);
		} catch (TemplateRenderException | RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new SystemException(e.getMessage(), e);
		}
	}

	public RenderResult doRender(String templateName, Map<String, ?> model, HttpServletRequest request,
			ReadOnlyResponse response, ParseConfig config) throws Exception {
		ParseContextHolder.getContext().setConfig(config);
		try {
			String content = doRender(templateName, model, request, response);
			MediaType type = ParseContextHolder.getContext().getMediaType();
			return new RenderResult(type, content);
		} catch (Throwable e) {
			markRollBack();

			// 从异常栈中寻找 逻辑异常
			Optional<Throwable> finded = ExceptionUtils.getFromChain(e, RuntimeLogicException.class,
					LockException.class, AuthencationException.class, RedirectException.class);
			if (finded.isPresent()) {
				throw (Exception) finded.get();
			}

			// 如果没有逻辑异常，转化模板异常
			Optional<TemplateRenderException> optional = templateExceptionTranslater.translate(templateName, e);
			if (optional.isPresent()) {
				throw optional.get();
			}

			throw new SystemException(e.getMessage(), e);

		} finally {
			commit();
			ParseContextHolder.remove();
		}
	}

	private void markRollBack() {
		TransactionStatus status = ParseContextHolder.getContext().getTransactionStatus();
		if (status != null) {
			status.setRollbackOnly();
		}
	}

	private void commit() {
		TransactionStatus status = ParseContextHolder.getContext().getTransactionStatus();
		if (status != null) {
			transactionManager.commit(status);
		}
	}

	private String doRender(String viewTemplateName, final Map<String, ?> model, final HttpServletRequest request,
			final ReadOnlyResponse response) throws Exception {
		Map<String, Object> _model = new HashMap<>();
		_model.putAll(getVariables(request));
		if (model != null) {
			_model.putAll(model);
		}
		return templateRenderer.execute(viewTemplateName, _model, request, response);
	}

	private Map<String, Object> getVariables(HttpServletRequest request) {
		Map<String, Object> map = new HashMap<>();
		if (!CollectionUtils.isEmpty(pros)) {
			for (Map.Entry<String, Object> it : pros.entrySet()) {
				map.put(it.getKey(), it.getValue());
			}
		}
		map.put("urls", urlHelper.getUrlsBySpace(Environment.getSpaceAlias()));
		map.put("user", Environment.getUser());
		map.put("messages", messages);
		map.put("space", Environment.getSpace());
		map.put("ip", Environment.getIP());
		LockBean lockBean = LockHelper.getLockBean(request);
		if (lockBean != null) {
			map.put("lock", lockBean.getLock());
		}
		return map;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(UIUtils.class));
		Set<BeanDefinition> definitions = new HashSet<>();
		definitions.addAll(scanner.findCandidateComponents("me.wangkang.blog"));
		for (BeanDefinition definition : definitions) {
			Class<?> clazz = Class.forName(definition.getBeanClassName());
			UIUtils ann = AnnotationUtils.findAnnotation(clazz, UIUtils.class);
			String name = ann.name();
			if (Validators.isEmptyOrNull(name, true)) {
				name = clazz.getSimpleName();
				name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
			}
			pros.put(name, clazz);
		}
	}

	public void setPros(Map<String, Object> pros) {
		this.pros = pros;
	}

}
