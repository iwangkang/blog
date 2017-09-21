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
package me.wangkang.blog.web.listener;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.web.template.TemplateService;

/**
 * session listener，用于监听管理员退出后清空预览页面缓存
 */
@Component
public class TemplateSessionListener implements HttpSessionListener, ApplicationContextAware {

	@Autowired
	private TemplateService templateService;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		if (applicationContext instanceof WebApplicationContext) {
			((WebApplicationContext) applicationContext).getServletContext().addListener(this);
		} else {
			throw new SystemException("必须处于WebApplicationContext中");
		}
	}

	@Override
	public void sessionCreated(HttpSessionEvent se) {
		//
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent se) {
		HttpSession old = se.getSession();
		if (old != null && old.getAttribute(Constants.USER_SESSION_KEY) != null) {
			// 清除预览模板
			templateService.clearPreview();
		}

	}
}