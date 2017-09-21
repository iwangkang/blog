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

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * 渲染模板内容
 */
public interface TemplateRenderExecutor {

	/**
	 * 
	 * @param viewTemplateName
	 * @param model
	 *            额外参数
	 * @param request
	 *            当前请求
	 * @param readOnlyResponse
	 *            <b>READ ONLY</b> response
	 * @return
	 * @throws Exception
	 */
	String execute(String viewTemplateName, Map<String, ?> model, HttpServletRequest request,
			ReadOnlyResponse readOnlyResponse) throws Exception;

}
