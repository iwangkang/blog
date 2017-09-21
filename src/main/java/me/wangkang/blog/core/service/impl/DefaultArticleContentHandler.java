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
package me.wangkang.blog.core.service.impl;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;

import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.entity.Editor;
import me.wangkang.blog.core.security.input.Markdown2Html;

/**
 * 
 * @author mhlx
 *
 */
public class DefaultArticleContentHandler implements ArticleContentHandler {

	@Autowired
	private Markdown2Html markdown2Html;

	@Override
	public void handle(Article article) {
		article.setContent(handleContent(article));
	}

	@Override
	public void handlePreview(Article article) {
		article.setContent(handleContent(article));
	}

	private String handleContent(Article article) {
		if (Editor.MD.equals(article.getEditor())) {
			return markdown2Html.toHtml(article.getContent());
		} else {
			return Jsoup.parseBodyFragment(article.getContent()).html();
		}
	}
}
