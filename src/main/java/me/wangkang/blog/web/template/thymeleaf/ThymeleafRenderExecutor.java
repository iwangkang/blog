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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.support.RequestContext;
import org.springframework.web.servlet.view.AbstractTemplateView;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebExpressionContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring4.expression.ThymeleafEvaluationContext;
import org.thymeleaf.spring4.naming.SpringContextVariableNames;
import org.thymeleaf.standard.expression.FragmentExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;
import org.thymeleaf.standard.expression.StandardExpressions;

import me.wangkang.blog.web.template.ReadOnlyResponse;
import me.wangkang.blog.web.template.TemplateRenderExecutor;

/**
 * 用来将模板解析成字符串
 * 
 * @author Administrator
 *
 */
public final class ThymeleafRenderExecutor implements TemplateRenderExecutor {

	@Autowired
	private ServletContext servletContext;
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private TemplateEngine viewTemplateEngine;

	private boolean exposeEvaluationContext = false;

	// ### copied from ThymeleafView
	@Override
	public String execute(String viewTemplateName, final Map<String, ?> model, final HttpServletRequest request,
			final ReadOnlyResponse response) throws Exception {

		Locale locale = LocaleContextHolder.getLocale();

		final Map<String, Object> mergedModel = new HashMap<>(30);

		// View.PATH_VARIABLES 只能获取被PathVariable annotation属性标记的属性
		// 这里需要获取optional PathVariable
		@SuppressWarnings("unchecked")
		final Map<String, Object> pathVars = (Map<String, Object>) request
				.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

		if (pathVars != null) {
			mergedModel.putAll(pathVars);
		}
		if (model != null) {
			mergedModel.putAll(model);
		}

		final RequestContext requestContext = new RequestContext(request, response, servletContext, mergedModel);

		// For compatibility with ThymeleafView
		addRequestContextAsVariable(mergedModel, SpringContextVariableNames.SPRING_REQUEST_CONTEXT, requestContext);
		// For compatibility with AbstractTemplateView
		addRequestContextAsVariable(mergedModel, AbstractTemplateView.SPRING_MACRO_REQUEST_CONTEXT_ATTRIBUTE,
				requestContext);

		if (exposeEvaluationContext) {
			final ConversionService conversionService = (ConversionService) request
					.getAttribute(ConversionService.class.getName());// 可能为null
			final ThymeleafEvaluationContext evaluationContext = new ThymeleafEvaluationContext(applicationContext,
					conversionService);
			mergedModel.put(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
					evaluationContext);
		}

		final IEngineConfiguration configuration = viewTemplateEngine.getConfiguration();
		final WebExpressionContext context = new WebExpressionContext(configuration, request, response, servletContext,
				locale, mergedModel);

		final String templateName;
		final Set<String> markupSelectors;
		if (!viewTemplateName.contains("::")) {
			// No fragment specified at the template name

			templateName = viewTemplateName;
			markupSelectors = null;

		} else {
			// Template name contains a fragment name, so we should parse it as
			// such

			final IStandardExpressionParser parser = StandardExpressions.getExpressionParser(configuration);

			final FragmentExpression fragmentExpression;
			try {
				// By parsing it as a standard expression, we might profit from
				// the expression cache
				fragmentExpression = (FragmentExpression) parser.parseExpression(context,
						"~{" + viewTemplateName + "}");
			} catch (final TemplateProcessingException e) {
				throw new IllegalArgumentException("Invalid template name specification: '" + viewTemplateName + "'",
						e);
			}

			final FragmentExpression.ExecutedFragmentExpression fragment = FragmentExpression
					.createExecutedFragmentExpression(context, fragmentExpression,
							StandardExpressionExecutionContext.NORMAL);

			templateName = FragmentExpression.resolveTemplateName(fragment);
			markupSelectors = FragmentExpression.resolveFragments(fragment);
			final Map<String, Object> nameFragmentParameters = fragment.getFragmentParameters();

			if (nameFragmentParameters != null) {

				if (fragment.hasSyntheticParameters()) {
					// We cannot allow synthetic parameters because there is no
					// way to specify them at the template
					// engine execution!
					throw new IllegalArgumentException(
							"Parameters in a view specification must be named (non-synthetic): '" + viewTemplateName
									+ "'");
				}

				context.setVariables(nameFragmentParameters);

			}

		}

		final Set<String> processMarkupSelectors;
		if (!CollectionUtils.isEmpty(markupSelectors)) {
			processMarkupSelectors = markupSelectors;
		} else {
			processMarkupSelectors = null;
		}

		return viewTemplateEngine.process(templateName, processMarkupSelectors, context);
	}

	private void addRequestContextAsVariable(final Map<String, Object> model, final String variableName,
			final RequestContext requestContext) throws TemplateProcessingException {

		if (model.containsKey(variableName)) {
			throw new TemplateProcessingException("属性" + variableName + "已经存在与request中");
		}
		model.put(variableName, requestContext);
	}

	public void setExposeEvaluationContext(boolean exposeEvaluationContext) {
		this.exposeEvaluationContext = exposeEvaluationContext;
	}
}
