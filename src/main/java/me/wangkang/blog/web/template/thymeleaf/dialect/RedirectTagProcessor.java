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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.UrlUtils;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.GlobalControllerExceptionHandler;
import me.wangkang.blog.web.template.RedirectException;

/**
 * 用于跳转页
 * <p>
 * 这个标签应该尽早的出现，因为它的出现意味着以前所有的解析都是无效的。 <br>
 * <b>应该谨慎的使用这个标签，错误的使用它可能会陷入无限的重定向循环，而且尽可能的用它做单一跳转，而不是连续的跳转(浏览器会对此做限制)</b>
 * </p>
 * <p>
 * <b>如果callable fragment中有该标签，那么ajax请求将会返回RedirectJsonResult，而不会返回目标页面内容</b>
 * <br>
 * </p>
 * 
 * @see GlobalControllerExceptionHandler#handleRedirectException(RedirectException,
 *      javax.servlet.http.HttpServletRequest,
 *      javax.servlet.http.HttpServletResponse)
 * @see RedirectException
 */
public class RedirectTagProcessor extends DefaultAttributesTagProcessor {

	private static final String TAG_NAME = "redirect";
	private static final int PRECEDENCE = 1000;
	private static final String URL_ATTR = "url";
	// 是否是301跳转
	private static final String MOVED_PERMANENTLY_ATTR = "permanently";
	private static final String CODE_ATTR = "code";
	private static final String ARGUMENT_SPLIT_ATTR = "argumentSpliter";
	private static final String ARGUMENTS_ATTR = "arguments";
	private static final String DEFAULT_MSG_ATTR = "defaultMsg";

	private final UrlHelper urlHelper;

	public RedirectTagProcessor(String dialectPrefix, ApplicationContext applicationContext) {
		super(TemplateMode.HTML, dialectPrefix, // Prefix to be applied to name
												// for matching
				TAG_NAME, // Tag name: match specifically this tag
				false, // Apply dialect prefix to tag name
				null, // No attribute name: will match by tag name
				false, // No prefix to be applied to attribute name
				PRECEDENCE); // Precedence (inside dialect's own precedence)\
		this.urlHelper = applicationContext.getBean(UrlHelper.class);
	}

	@Override
	protected void doProcess(ITemplateContext context, IProcessableElementTag tag,
			IElementTagStructureHandler structureHandler) {

		structureHandler.removeElement();

		Map<String, String> attMap = processAttribute(context, tag);

		String url = attMap.get(URL_ATTR);
		if (url == null) {
			return;
		}

		String redirectUrl = url;
		if (!UrlUtils.isAbsoluteUrl(redirectUrl)) {
			redirectUrl = urlHelper.getUrl() + "/" + FileUtils.cleanPath(redirectUrl);
		}

		URL _url = null;
		try {
			_url = new URL(redirectUrl);
		} catch (MalformedURLException e) {
			// invalid url
			// ignore
		}

		if (_url != null) {
			RedirectException ex = new RedirectException(_url.toString(),
					Boolean.parseBoolean(attMap.get(MOVED_PERMANENTLY_ATTR)));
			if (!ex.isPermanently()) {
				String code = attMap.get(CODE_ATTR);
				if (!Validators.isEmptyOrNull(code, true)) {
					String defaultMsg = attMap.get(DEFAULT_MSG_ATTR);
					String[] argumentsArray = {};
					String arguments = attMap.get(ARGUMENTS_ATTR);
					String argumentSpliter = attMap.getOrDefault(ARGUMENT_SPLIT_ATTR, ",");
					if (arguments != null) {
						argumentsArray = arguments.split(argumentSpliter);
					}
					ex.setRedirectMsg(new Message(code, defaultMsg, (Object[]) argumentsArray));
				}
			}
			throw ex;
		}
	}

}
