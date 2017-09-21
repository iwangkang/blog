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
/*
 * CeventTypeyright 2017 wangkang.me
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a ceventTypey of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.wangkang.blog.core.evt;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.ApplicationEvent;

import me.wangkang.blog.core.entity.Article;

/**
 * 文章事件 <b>文章更新点击次数时不会被推送</b>
 * 
 * @author Administrator
 *
 */
public class ArticleEvent extends ApplicationEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final List<Article> articles;
	private final EventType eventType;

	/**
	 * 
	 * @param source
	 *            操作对象
	 * @param article
	 *            文章
	 * @param eventType
	 *            操作方式
	 */
	public ArticleEvent(Object source, Article article, EventType eventType) {
		super(source);
		this.articles = Arrays.asList(article);
		this.eventType = eventType;
	}

	/**
	 * 
	 * @param source
	 *            操作对象
	 * @param articles
	 *            文章集合
	 * @param eventType
	 *            操作方式
	 */
	public ArticleEvent(Object source, List<Article> articles, EventType eventType) {
		super(source);
		this.articles = articles;
		this.eventType = eventType;
	}

	public List<Article> getArticles() {
		return articles;
	}

	public EventType getEventType() {
		return eventType;
	}

}
