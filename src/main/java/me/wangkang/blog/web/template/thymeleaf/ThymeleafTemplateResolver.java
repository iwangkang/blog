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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.cache.AlwaysValidCacheEntryValidity;
import org.thymeleaf.cache.ICacheEntryValidity;
import org.thymeleaf.cache.NonCacheableCacheEntryValidity;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolution;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.web.template.Template;
import me.wangkang.blog.web.template.TemplateService;

public class ThymeleafTemplateResolver implements ITemplateResolver {

	@Autowired
	private TemplateService templateService;

	static final int ORDER = 1;

	@Override
	public String getName() {
		return this.getClass().getName();
	}

	@Override
	public Integer getOrder() {
		return ORDER;
	}

	@Override
	public TemplateResolution resolveTemplate(IEngineConfiguration configuration, String ownerTemplate,
			String templateName, Map<String, Object> templateResolutionAttributes) {
		if (!Template.isTemplate(templateName)) {
			return null;
		}

		ITemplateResource templateResource;
		Optional<Template> optional = templateService.queryTemplate(templateName);
		if (optional.isPresent()) {
			templateResource = new TemplateResource(optional.get());
		} else {
			templateResource = new StringTemplateResource("");
		}

		ICacheEntryValidity cacheEntryValidity = Template.isPreviewTemplate(templateName)
				? NonCacheableCacheEntryValidity.INSTANCE : AlwaysValidCacheEntryValidity.INSTANCE;

		return new TemplateResolution(templateResource, false, TemplateMode.HTML, false, cacheEntryValidity);
	}

	public final class TemplateResource implements ITemplateResource {

		private final Template template;

		private TemplateResource(Template template) {
			super();
			this.template = template;
		}

		@Override
		public String getDescription() {
			return "";
		}

		@Override
		public String getBaseName() {
			return null;
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public Reader reader() throws IOException {
			return new StringReader(template.getTemplate());
		}

		@Override
		public ITemplateResource relative(String relativeLocation) {
			throw new SystemException("unsupport");
		}

		public Template getTemplate() {
			return template;
		}
	}
}
