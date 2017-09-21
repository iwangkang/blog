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
package me.wangkang.blog.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.view.feed.AbstractRssFeedView;

import com.rometools.rome.feed.rss.Channel;
import com.rometools.rome.feed.rss.Content;
import com.rometools.rome.feed.rss.Item;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.pageparam.PageResult;
import me.wangkang.blog.core.security.Environment;

@Component
public class RssView extends AbstractRssFeedView {

	@Autowired
	private UrlHelper urlHelper;

	@Override
	protected List<Item> buildFeedItems(Map<String, Object> model, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		@SuppressWarnings("unchecked")
		PageResult<Article> page = (PageResult<Article>) model.get("page");
		return page.getDatas().stream().map(this::toItem).collect(Collectors.toList());
	}

	private Item toItem(Article article) {
		Item item = new Item();
		Content content = new Content();
		content.setValue(article.getSummary());
		item.setContent(content);
		item.setTitle(article.getTitle());
		item.setLink(urlHelper.getUrls().getUrl(article));
		item.setPubDate(article.getPubDate());
		return item;
	}

	@Override
	protected void buildFeedMetadata(Map<String, Object> model, Channel feed, HttpServletRequest request) {
		if (!Environment.hasSpace()) {
			feed.setLink(urlHelper.getUrl());
			String domain = urlHelper.getUrlConfig().getDomain();
			feed.setDescription(domain);
			feed.setTitle(domain);
		} else {
			Space space = Environment.getSpace();
			feed.setTitle(space.getName());
			feed.setDescription(space.getName());
			feed.setLink(urlHelper.getUrls().getUrl(space));
		}
		feed.setEncoding(Constants.CHARSET.name());
	}

}
