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
package me.wangkang.blog.web.template.thymeleaf.dialect;

import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springframework.context.ApplicationContext;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.RuntimeLogicException;
import me.wangkang.blog.core.vo.DataBind;
import me.wangkang.blog.core.vo.DataTag;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.template.ParseContextHolder;
import me.wangkang.blog.web.template.TemplateService;

/**
 * {@link http://www.thymeleaf.org/doc/tutorials/3.0/extendingthymeleaf.html#creating-our-own-dialect}
 * 
 * @author mhlx
 *
 */
public class DataTagProcessor extends DefaultAttributesTagProcessor {

	private static final String TAG_NAME = "data";
	private static final int PRECEDENCE = 1000;
	private static final String NAME_ATTR = "name";

	private final TemplateService templateService;

	public DataTagProcessor(String dialectPrefix, ApplicationContext applicationContext) {
		super(TemplateMode.HTML, dialectPrefix, // Prefix to be applied to name
												// for matching
				TAG_NAME, // Tag name: match specifically this tag
				false, // Apply dialect prefix to tag name
				null, // No attribute name: will match by tag name
				false, // No prefix to be applied to attribute name
				PRECEDENCE); // Precedence (inside dialect's own precedence)
		this.templateService = applicationContext.getBean(TemplateService.class);
	}

	@Override
	protected final void doProcess(ITemplateContext context, IProcessableElementTag tag,
			IElementTagStructureHandler structureHandler) {
		try {

			DataTag dataTag = buildDataTag(context, tag);

			if (dataTag == null) {
				return;
			}

			IWebContext webContext = (IWebContext) context;
			Optional<DataBind> optional = queryDataBind(dataTag);
			if (optional.isPresent()) {
				DataBind bind = optional.get();
				HttpServletRequest request = webContext.getRequest();
				if (request.getAttribute(bind.getDataName()) != null) {
					throw new TemplateProcessingException("属性" + bind.getDataName() + "已经存在于request中");
				}
				request.setAttribute(bind.getDataName(), bind.getData());
			}
		} finally {
			structureHandler.removeElement();
		}
	}

	private Optional<DataBind> queryDataBind(DataTag dataTag) {
		try {
			return templateService.queryData(dataTag, ParseContextHolder.getContext().onlyCallable()
					&& !ParseContextHolder.getContext().getRoot().isCallable());
		} catch (LogicException e) {
			throw new RuntimeLogicException(e);
		}
	}

	private DataTag buildDataTag(ITemplateContext context, IProcessableElementTag tag) {
		Map<String, String> attMap = processAttribute(context, tag);
		String name = attMap.get(NAME_ATTR);
		if (Validators.isEmptyOrNull(name, true)) {
			return null;
		}
		return new DataTag(name, attMap);
	}
}
