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
package me.wangkang.blog.support.sitemap;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.config.UrlHelper.SpaceUrls;
import me.wangkang.blog.core.dao.ArticleDao;
import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.service.impl.Transactions;

public class XmlSiteMap implements InitializingBean {

	@Autowired
	private UrlHelper urlHelper;
	@Autowired
	private ArticleDao articleDao;
	@Autowired
	private PlatformTransactionManager platformTransactionManager;

	private final Comparator<SiteMapUrlItem> siteMapUrlItemComparator = new Comparator<SiteMapUrlItem>() {

		@Override
		public int compare(SiteMapUrlItem o1, SiteMapUrlItem o2) {
			if (o1.getLastmod() != null && o2.getLastmod() != null) {
				return -o1.getLastmod().compareTo(o2.getLastmod());
			}
			return 0;
		}
	};

	private SiteMapConfigure configure;

	private final Comparator<Article> lastModifyDateComparator = new Comparator<Article>() {

		@Override
		public int compare(Article o1, Article o2) {
			Timestamp lastModifyDate1 = o1.getLastModifyDate();
			Timestamp lastModifyDate2 = o2.getLastModifyDate();
			if (lastModifyDate1 == null && lastModifyDate2 != null) {
				return 1;
			}

			if (lastModifyDate1 != null && lastModifyDate2 == null) {
				return -1;
			}

			if (lastModifyDate1 == null && lastModifyDate2 == null) {
				int compare = -o1.getPubDate().compareTo(o2.getPubDate());
				if (compare == 0) {
					compare = -o1.getId().compareTo(o2.getId());
				}
				return compare;
			}

			if (lastModifyDate1 != null && lastModifyDate2 != null) {
				int compare = -lastModifyDate1.compareTo(lastModifyDate2);
				if (compare == 0) {
					compare = -o1.getPubDate().compareTo(o2.getPubDate());
					if (compare == 0) {
						compare = -o1.getId().compareTo(o2.getId());
					}
				}
				return compare;
			}
			return 0;
		}
	};

	private String siteMapXml;

	public String getSiteMap() {
		synchronized (this) {
			if (siteMapXml == null) {
				siteMapXml = buildSiteMapXml();
			}
			return siteMapXml;
		}
	}

	public synchronized void updateSitemap() {
		this.siteMapXml = null;
	}

	private String buildSiteMapXml() {
		List<SiteMapUrlItem> items = querySiteMapItems();
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
		for (SiteMapUrlItem item : items) {
			sb.append(item.toBuilder());
		}
		sb.append("</urlset>");
		return sb.toString();
	}

	@SuppressWarnings("deprecation")
	protected List<SiteMapUrlItem> querySiteMapItems() {

		List<Article> articles = Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
			return articleDao.selectPublished();
		});

		SpaceUrls urls = urlHelper.getUrlsBySpace(null);
		List<SiteMapUrlItem> items = new ArrayList<>();

		List<SiteMapUrlItem> articlesItems = new ArrayList<>();
		for (Article article : articles) {
			SiteMapConfig config = configure.getConfig(article);
			articlesItems.add(new SiteMapUrlItem(urls.getUrl(article),
					article.getLastModifyDate() == null ? article.getPubDate() : article.getLastModifyDate(),
					config.getFreq(), config.getFormattedPriority()));
		}
		Collections.sort(articlesItems, siteMapUrlItemComparator);
		items.addAll(articlesItems);

		List<SiteMapUrlItem> spaceItems = new ArrayList<>();
		Map<Space, List<Article>> map = articles.stream().collect(Collectors.groupingBy(Article::getSpace));
		for (Map.Entry<Space, List<Article>> entry : map.entrySet()) {
			Space space = entry.getKey();
			SiteMapConfig config = configure.getConfig(space);
			Timestamp lastmod = entry.getValue().stream().min(lastModifyDateComparator)
					.map(article -> (article.getLastModifyDate() == null ? article.getPubDate()
							: article.getLastModifyDate()))
					.orElse(null);
			spaceItems.add(
					new SiteMapUrlItem(urls.getUrl(space), lastmod, config.getFreq(), config.getFormattedPriority()));
		}
		Collections.sort(spaceItems, siteMapUrlItemComparator);
		items.addAll(spaceItems);

		return items;
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		if (configure == null) {
			configure = o -> {
				if (o instanceof Article) {
					return new SiteMapConfig(Changefreq.MONTHLY, 0.8F);
				}
				if (o instanceof Space) {
					return new SiteMapConfig(Changefreq.WEEKLY, 0.6F);
				}
				return new SiteMapConfig(Changefreq.MONTHLY, 0.8F);
			};
		}

	}

	public void setConfigure(SiteMapConfigure configure) {
		this.configure = configure;
	}
}
