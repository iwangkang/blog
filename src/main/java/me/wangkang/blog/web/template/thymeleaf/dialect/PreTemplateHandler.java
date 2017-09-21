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

import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.templateresource.ITemplateResource;

import me.wangkang.blog.core.exception.RuntimeLogicException;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.web.template.ParseContextHolder;
import me.wangkang.blog.web.template.Template;
import me.wangkang.blog.web.template.thymeleaf.ThymeleafTemplateResolver.TemplateResource;

/**
 * 不希望通过replace等方式再次渲染页面
 * 
 * @author mhlx
 *
 */
public final class PreTemplateHandler extends AbstractTemplateHandler {

	public PreTemplateHandler() {
		super();
	}

	@Override
	public void setContext(ITemplateContext context) {
		TemplateData templateData = context.getTemplateData();
		String templateName = templateData.getTemplate();
		if (Template.isTemplate(templateName)) {
			ITemplateResource templateResource = templateData.getTemplateResource();

			if (templateResource instanceof TemplateResource) {
				// TemplateResource 可能来自于缓存，为了防止修改数据，这里clone后传给页面
				Template template = ((TemplateResource) templateResource).getTemplate();

				Template root = ParseContextHolder.getContext().getRoot();
				// 如果主模板不存在，设置主模板
				if (root == null) {
					ParseContextHolder.getContext().setRoot(template);
				} else {
					// 如果已经存在主模板，那么查看当前待解析的模板是否是'主模板'
					if (template.isRoot()) {
						throw new TemplateProcessingException("无法再次处理模板:" + templateName);
					}
				}

				if (!ParseContextHolder.getContext().getRoot().isCallable()
						&& ParseContextHolder.getContext().onlyCallable()) {
					throw new RuntimeLogicException(new Message("template.notCallable", "模板无法被调用"));
				}

				((IEngineContext) context).setVariable("this", template.cloneTemplate());
			}
		}
		super.setContext(context);
	}
}