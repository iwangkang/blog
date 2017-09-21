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
package me.wangkang.blog.support.ping;

import org.springframework.beans.factory.annotation.Autowired;

import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.entity.Article;

public abstract class PingService {

	@Autowired
	protected UrlHelper urlHelper;

	/**
	 * ping
	 * 
	 * @param article
	 *            文章
	 * @param blogName
	 *            博客名
	 * @throws PingException
	 *             ping异常
	 * @throws Exception
	 */
	public abstract void ping(Article article, String blogName) throws Exception;

}
