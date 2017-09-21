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

public class TemplateRenderException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final String templateName;
	private final TemplateRenderErrorDescription renderErrorDescription;

	public TemplateRenderException(String templateName, TemplateRenderErrorDescription description, Throwable ex) {
		super(ex);
		this.renderErrorDescription = description;
		this.templateName = templateName;
	}

	public TemplateRenderErrorDescription getRenderErrorDescription() {
		return renderErrorDescription;
	}

	/**
	 * root template name
	 * 
	 * @return
	 */
	public String getTemplateName() {
		return templateName;
	}
}