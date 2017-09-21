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
package me.wangkang.blog.core.security.input;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.util.Jsons;
import me.wangkang.blog.util.Resources;
import me.wangkang.blog.util.UrlUtils;
import me.wangkang.blog.util.Validators;

/**
 * 默认的html文本清理器
 * 
 * @author Administrator
 *
 */
public class DefaultHtmlClean implements HtmlClean, InitializingBean {

	@Autowired
	private UrlHelper urlHelper;

	/**
	 * whitelist的json配置,请小心配置注意xss，当且仅当配置评论允许html之后才会生效;
	 * 
	 * @see JsonWhitelist
	 */
	private Resource whitelistJsonResource;
	private AllowTags tags;
	private boolean nofollow = true;// 是否在超链接上加上nofollow属性

	private static final String NOFOLLOW_ATTR = "external nofollow";

	@Override
	public String clean(String html) {
		Document body = Jsoup.parseBodyFragment(html);
		if (nofollow) {
			Elements eles = body.select("a[href]");
			eles.stream().filter(ele -> needNofollow(ele.attr("href"))).forEach(ele -> ele.attr("rel", NOFOLLOW_ATTR));
		}
		return Jsoup.clean(body.html(), InnerWhitelist.configured(tags));
	}

	private boolean needNofollow(String href) {
		if (UrlUtils.isAbsoluteUrl(href) && (StringUtils.startsWithIgnoreCase(href, "http://")
				|| StringUtils.startsWithIgnoreCase(href, "https://"))) {
			UriComponents uc = UriComponentsBuilder.fromHttpUrl(href).build();
			String host = uc.getHost();
			if (StringUtils.endsWithIgnoreCase(host, urlHelper.getUrlConfig().getRootDomain())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (whitelistJsonResource != null) {
			tags = Jsons.readValue(AllowTags.class, Resources.readResourceToString(whitelistJsonResource));
		}
		if (tags == null) {
			tags = new AllowTags();
			tags.addSimpleTags(new String[] { "b", "code", "em", "del", "small", "strong" });
			Tag a = new Tag("a").
			// 这里protocols必须要标明，否则会有xss
					addAttributes(new Attribute("href", "ftp", "http", "https", "mailto"), new Attribute("rel"));
			tags.addTag(a);
		}
	}

	private static final class InnerWhitelist extends Whitelist {
		InnerWhitelist(AllowTags tags) {
			for (Tag tag : tags.getTags()) {
				addTags(tag.getName());
				for (Attribute att : tag.getAttributes()) {
					addAttributes(tag.getName(), att.getName());
					if (!Validators.isEmptyOrNull(att.getProtocols(), true)) {
						String protocols = att.getProtocols().trim();
						for (String protocol : protocols.split(",")) {
							addProtocols(tag.getName(), att.getName(), protocol);
						}
					}
					if (!Validators.isEmptyOrNull(att.getEnforce(), true)) {
						String enforce = att.getEnforce().trim();
						addEnforcedAttribute(tag.getName(), att.getName(), enforce);
					}
				}
			}
		}

		static Whitelist configured(AllowTags tags) {
			return new InnerWhitelist(tags);
		}
	}

	public void setTags(AllowTags tags) {
		this.tags = tags;
	}

	public void setWhitelistJsonResource(Resource whitelistJsonResource) {
		this.whitelistJsonResource = whitelistJsonResource;
	}

	public void setNofollow(boolean nofollow) {
		this.nofollow = nofollow;
	}
}
