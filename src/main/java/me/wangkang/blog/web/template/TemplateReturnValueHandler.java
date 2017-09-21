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

import java.io.Writer;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.web.TemplateView;
import me.wangkang.blog.web.Webs;

public class TemplateReturnValueHandler implements HandlerMethodReturnValueHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(TemplateReturnValueHandler.class);

	private TemplateRender templateRender;

	public TemplateReturnValueHandler(TemplateRender templateRender) {
		this.templateRender = templateRender;
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return TemplateView.class.isAssignableFrom(returnType.getParameterType());
	}

	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest) throws Exception {
		mavContainer.setRequestHandled(true);
		HttpServletResponse nativeResponse = webRequest.getNativeResponse(HttpServletResponse.class);
		HttpServletRequest nativeRequest = webRequest.getNativeRequest(HttpServletRequest.class);

		TemplateView templateView = (TemplateView) returnValue;
		Objects.requireNonNull(templateView);

		String templateName = templateView.getTemplateName();

		RenderResult rendered;

		try {
			rendered = templateRender.doRender(templateName, mavContainer.getModel(), nativeRequest, new ReadOnlyResponse(nativeResponse),
					new ParseConfig());

		} catch (RedirectException e) {
			throw e;
		} catch (Exception e) {

			// 解锁页面不能出现异常，不再跳转(防止死循环)
			if (Webs.unlockRequest(nativeRequest) && "get".equalsIgnoreCase(nativeRequest.getMethod())) {
				LOGGER.error("在解锁页面发生了一个异常，为了防止死循环，这个页面发生异常将会无法跳转，异常栈信息:" + e.getMessage(), e);
				return;
			}

			throw e;
		}

		String content = rendered.getContent();

		MediaType type = rendered.getType();
		if (type == null) {
			type = MediaType.TEXT_HTML;
		}

		nativeResponse.setContentType(type.toString());
		nativeResponse.setCharacterEncoding(Constants.CHARSET.name());

		Writer writer = nativeResponse.getWriter();
		writer.write(content);
		writer.flush();
	}

}
