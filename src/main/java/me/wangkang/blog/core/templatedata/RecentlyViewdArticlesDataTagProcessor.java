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
package me.wangkang.blog.core.templatedata;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.service.ArticleService;

public class RecentlyViewdArticlesDataTagProcessor extends DataTagProcessor<List<Article>> {

	@Autowired
	private ArticleService articleService;

	private final int max;

	public RecentlyViewdArticlesDataTagProcessor(String name, String dataName, int max) {
		super(name, dataName);
		this.max = max;

		if (max < 0) {
			throw new SystemException("最大查询数量不能小于0");
		}
	}

	public RecentlyViewdArticlesDataTagProcessor(String name, String dataName) {
		this(name, dataName, 10);
	}

	@Override
	protected List<Article> query(Attributes attributes) throws LogicException {
		Integer num = attributes.getInteger("num", max);
		if (num <= 0 || num > max) {
			num = max;
		}
		return articleService.getRecentlyViewdArticle(num);
	}

}
