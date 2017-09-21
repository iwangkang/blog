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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.web.util.HtmlUtils;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.util.Times;
import me.wangkang.blog.util.Validators;

public class SiteMapUrlItem {

	private final String loc;
	private final Timestamp lastmod;
	private final Changefreq changefreq;
	private final String priority;

	public SiteMapUrlItem(String loc, Timestamp lastmod, Changefreq changefreq, String priority) {
		super();
		this.loc = cleanUrl(loc);
		this.lastmod = lastmod;
		this.changefreq = changefreq;
		this.priority = priority;
	}

	public StringBuilder toBuilder() {
		StringBuilder sb = new StringBuilder();
		sb.append("<url>");
		sb.append("<loc>").append(loc).append("</loc>");
		if (lastmod != null) {
			sb.append("<lastmod>")
					.append(Times.format(ZonedDateTime.of(lastmod.toLocalDateTime(), ZoneId.systemDefault()),
							"yyyy-MM-dd'T'HH:mm:ssXXX"))
					.append("</lastmod>");
		}
		if (changefreq != null) {
			sb.append("<changefreq>").append(changefreq.name().toLowerCase()).append("</changefreq>");
		}
		if (priority != null) {
			sb.append("<priority>").append(priority).append("</priority>");
		}
		sb.append("</url>");
		return sb;
	}

	private String cleanUrl(String url) {
		return HtmlUtils.htmlEscape(url, Constants.CHARSET.name());
	}

	public Timestamp getLastmod() {
		return lastmod;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.loc);
	}

	@Override
	public boolean equals(Object obj) {
		if (Validators.baseEquals(this, obj)) {
			SiteMapUrlItem rhs = (SiteMapUrlItem) obj;
			return Objects.equals(this.loc, rhs.loc);
		}
		return false;
	}
}