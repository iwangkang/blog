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

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.MapBindingResult;

import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.entity.Article.ArticleFrom;
import me.wangkang.blog.core.entity.Article.ArticleStatus;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.pageparam.ArticleQueryParam;
import me.wangkang.blog.core.pageparam.PageResult;
import me.wangkang.blog.core.pageparam.ArticleQueryParam.Sort;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.ArticleService;
import me.wangkang.blog.util.Times;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.validator.ArticleQueryParamValidator;

/**
 * 文章列表数据处理器
 * 
 * @author Administrator
 *
 */
public class ArticlesDataTagProcessor extends DataTagProcessor<PageResult<Article>> {

	@Autowired
	private ArticleQueryParamValidator validator;
	@Autowired
	private ArticleService articleService;

	/**
	 * 构造器
	 * 
	 * @param name
	 *            数据处理器名称
	 * @param dataName
	 *            页面dataName
	 */
	public ArticlesDataTagProcessor(String name, String dataName) {
		super(name, dataName);
	}

	@Override
	protected PageResult<Article> query(Attributes attributes) throws LogicException {
		ArticleQueryParam param = buildFromAttributes(attributes);
		return articleService.queryArticle(param);
	}

	private ArticleQueryParam buildFromAttributes(Attributes attributes) {
		ArticleQueryParam param = new ArticleQueryParam();

		String beginStr = attributes.get("begin");
		String endStr = attributes.get("end");
		if (beginStr != null && endStr != null) {
			param.setBegin(Times.parseAndGetDate(beginStr));
			param.setEnd(Times.parseAndGetDate(endStr));
		}

		String query = attributes.get("query");
		if (!Validators.isEmptyOrNull(query, true)) {
			param.setQuery(query);
		}

		param.setFrom(attributes.getEnum("from", ArticleFrom.class, null));
		param.setTag(attributes.get("tag"));
		param.setSort(attributes.getEnum("sort", Sort.class, null));
		param.setCurrentPage(attributes.getInteger("currentPage", 0));
		param.setPageSize(attributes.getInteger("pageSize", 0));
		param.setHighlight(attributes.getBoolean("highlight", true));
		param.setIgnoreLevel(attributes.getBoolean("ignoreLevel", false));
		param.setQueryLock(attributes.getBoolean("queryLock", true));
		param.setSpaces(attributes.getSet("spaces", ","));

		if (Environment.isLogin()) {
			param.setQueryPrivate(attributes.getBoolean("queryPrivate", true));
		}

		param.setSpace(getCurrentSpace());
		param.setStatus(ArticleStatus.PUBLISHED);

		validator.validate(param, new MapBindingResult(new HashMap<>(), "articleQueryParam"));
		return param;
	}
}
