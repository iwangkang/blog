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

/**
 * 没有发现模板异常
 * <p>
 * <b>逻辑异常，无需日志记录</b>
 * </p>
 * 
 * @author mhlx
 *
 */
public final class TemplateNotFoundException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final String templateName;

	public TemplateNotFoundException(String templateName) {
		super();
		this.templateName = templateName;
	}

	public String getTemplateName() {
		return templateName;
	}

	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}

}
