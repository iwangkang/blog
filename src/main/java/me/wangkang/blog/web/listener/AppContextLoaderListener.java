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

import java.lang.reflect.Method;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Enumeration;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import me.wangkang.blog.core.config.UrlConfig;

public class AppContextLoaderListener extends ContextLoaderListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(AppContextLoaderListener.class);

	@Override
	public void contextInitialized(ServletContextEvent event) {
		super.contextInitialized(event);
		WebApplicationContext ctx = getCurrentWebApplicationContext();
		UrlConfig helper = ctx.getBean(UrlConfig.class);
		ServletContext sc = event.getServletContext();
		EnumSet<SessionTrackingMode> modes = EnumSet.of(SessionTrackingMode.COOKIE);
		sc.setSessionTrackingModes(modes);
		SessionCookieConfig config = sc.getSessionCookieConfig();
		config.setHttpOnly(true);
		config.setSecure(helper.isSecure());
		if (!helper.isLocalDomain()) {
			config.setDomain(helper.getRootDomain());
		}
		String contextPath = helper.getContextPath();
		if (contextPath.isEmpty()) {
			config.setPath("/");
		} else {
			config.setPath(contextPath);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		super.contextDestroyed(event);

		ClassLoader cl = Thread.currentThread().getContextClassLoader();

		Enumeration<Driver> drivers = DriverManager.getDrivers();
		while (drivers.hasMoreElements()) {
			Driver driver = drivers.nextElement();
			if (driver.getClass().getClassLoader() == cl) {
				try {
					DriverManager.deregisterDriver(driver);
				} catch (SQLException e) {
					LOGGER.warn("注销driver失败：" + e.getMessage(), e);
				}
			}
		}

		shutdownMysqlIfAvailable();
	}

	private void shutdownMysqlIfAvailable() {
		try {
			Class<?> clazz = Class.forName("com.mysql.jdbc.AbandonedConnectionCleanupThread");
			// 5.1.41
			Method method = clazz.getMethod("checkedShutdown");
			method.invoke(null);
		} catch (Exception e) {
			// ignore
		}
	}

}
