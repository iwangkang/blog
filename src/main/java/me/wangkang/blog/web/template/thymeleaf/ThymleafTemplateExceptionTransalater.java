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

import java.util.List;
import java.util.Optional;

import org.springframework.util.StringUtils;
import org.springframework.web.context.support.ServletContextResource;
import org.thymeleaf.exceptions.TemplateProcessingException;

import me.wangkang.blog.util.ExceptionUtils;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.template.TemplateExceptionTranslater;
import me.wangkang.blog.web.template.TemplateRenderErrorDescription;
import me.wangkang.blog.web.template.TemplateRenderException;
import me.wangkang.blog.web.template.TemplateRenderErrorDescription.TemplateErrorInfo;

public class ThymleafTemplateExceptionTransalater implements TemplateExceptionTranslater {

	private static final String SPEL_EXPRESSION_ERROR_PREFIX = "Exception evaluating SpringEL expression:";
	/**
	 * @see ServletContextResource#getDescription()
	 */
	private static final String STANDARD_EXPRESSION_ERROR_PREFIX = "Could not parse as expression:";

	private static final String SERVLET_RESOURCE_PREFIX = "ServletContext resource ";

	@Override
	public Optional<TemplateRenderException> translate(String templateName, Throwable e) {
		if (e instanceof TemplateProcessingException) {
			Optional.of(new TemplateRenderException(templateName,
					fromException((TemplateProcessingException) e, templateName), e));
		}
		if (e instanceof UIStackoverflowError) {
			UIStackoverflowError error = (UIStackoverflowError) e;
			Optional.of(new TemplateRenderException(templateName, fromError(error), e));
		}
		return Optional.empty();
	}

	private TemplateRenderErrorDescription fromError(UIStackoverflowError e) {
		TemplateRenderErrorDescription description = new TemplateRenderErrorDescription();
		description.addTemplateErrorInfos(
				new TemplateErrorInfo(parseTemplateName(e.getTemplateName()), e.getLine(), e.getCol()));
		return description;
	}

	private TemplateRenderErrorDescription fromException(TemplateProcessingException e, String templateName) {
		TemplateRenderErrorDescription description = new TemplateRenderErrorDescription();
		List<Throwable> ths = ExceptionUtils.getThrowableList(e);
		TemplateProcessingException last = null;
		for (Throwable th : ths) {
			if (TemplateProcessingException.class.isAssignableFrom(th.getClass())) {
				TemplateProcessingException templateProcessingException = (TemplateProcessingException) th;
				String templateName2 = templateProcessingException.getTemplateName();
				if (!Validators.isEmptyOrNull(templateName2, true)) {
					templateName2 = parseTemplateName(templateName2);
					description.addTemplateErrorInfos(new TemplateErrorInfo(templateName2,
							templateProcessingException.getLine(), templateProcessingException.getCol()));
					last = templateProcessingException;
				}
			}
		}
		if (last != null) {
			last.setTemplateName(null);
			description.setExpression(tryGetExpression(last.getMessage()));
		}
		return description;
	}

	private String tryGetExpression(String errorMsg) {
		if (errorMsg.startsWith(SPEL_EXPRESSION_ERROR_PREFIX)) {
			String expression = StringUtils.delete(errorMsg, SPEL_EXPRESSION_ERROR_PREFIX).trim();
			return expression.substring(1, expression.length() - 1);
		}
		if (errorMsg.startsWith(STANDARD_EXPRESSION_ERROR_PREFIX)) {
			String expression = StringUtils.delete(errorMsg, STANDARD_EXPRESSION_ERROR_PREFIX).trim();
			return expression.substring(1, expression.length() - 1);
		}
		return null;
	}

	private String parseTemplateName(String name) {
		if (!Validators.isEmptyOrNull(name, true) && name.startsWith(SERVLET_RESOURCE_PREFIX)) {
			int first = name.indexOf('[');
			int last = name.lastIndexOf(']');
			if (first != -1 && last != -1) {
				return name.substring(first + 1, last);
			}
		}
		return name;
	}

}
