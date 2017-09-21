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
package me.wangkang.blog.core.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.util.Validators;

/**
 * 用于基本的路径配置<br>
 * 在项目中，这些通常不会频繁变动，所以统一进行设置
 * 
 * @author mhlx
 *
 */
@Component
public class UrlConfig implements InitializingBean {

	private static final String LOCAL_HOST = "localhost";

	@Value("${app.contextPath:''}")
	private String contextPath;

	// 如果开启了enableCategoryDomain
	// 必须提供这个域名，否则无法判断是否是category级别的访问
	@Value("${app.domain:'localhost'}")
	private String domain;

	// 如果开启了enableCategoryDomain
	// 必须提供这个域名，否则无法判断是否是category级别的访问
	@Value("${app.port:'80'}")
	private int port;

	@Value("${app.schema}")
	private String schema;

	private String rootDomain;

	public int getPort() {
		return port;
	}

	public String getContextPath() {
		return contextPath;
	}

	public String getDomain() {
		return domain;
	}

	public String getSchema() {
		return schema;
	}

	public String getRootDomain() {
		return rootDomain == null ? domain : rootDomain;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (Validators.isEmptyOrNull(domain, true)) {
			domain = LOCAL_HOST;
		}
		domain = domain.toLowerCase();
		if (domain.indexOf('.') == -1) {
			rootDomain = domain;
		} else {
			String[] splitResult = domain.split("\\.");
			String last = splitResult[splitResult.length - 1];
			if (!Validators.isAlpha(last)) {
				throw new SystemException("错误的域名:" + domain);
			}

			// www.abc.com
			// abc.com
			if (domain.startsWith("www.") && splitResult.length == 3) {
				rootDomain = splitResult[1] + "." + splitResult[2];
			} else {
				rootDomain = domain;
			}
		}
		contextPath = contextPath.trim();

		if (!contextPath.isEmpty() && !contextPath.startsWith("/")) {
			contextPath = "/" + contextPath;
		}
	}

	/**
	 * 判断是否是本地环境
	 * 
	 * @return
	 */
	public boolean isLocalDomain() {
		return LOCAL_HOST.equalsIgnoreCase(this.domain);
	}

	/**
	 * 判断是否https
	 * 
	 * @return
	 */
	public boolean isSecure() {
		return "https".equalsIgnoreCase(this.schema);
	}
}
