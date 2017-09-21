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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 用来将markdown文本转化为html文本 <br>
 * {@link https://github.com/atlassian/commonmark-java}
 * 
 * @author Administrator
 *
 */
@Component
public class CommonMarkdown2Html implements Markdown2Html, InitializingBean {

	private List<Extension> extensions = new ArrayList<>();

	/**
	 * singleton?
	 */
	private Parser parser;
	private HtmlRenderer renderer;

	private static final List<Extension> BASE_EXTENSIONS = Arrays.asList(AutolinkExtension.create(),
			TablesExtension.create(), StrikethroughExtension.create(), HeadingAnchorExtension.create());

	@Override
	public String toHtml(String markdown) {
		if (markdown == null) {
			return "";
		}
		Node document = parser.parse(markdown);
		return renderer.render(document);
	}

	public void setExtensions(List<Extension> extensions) {
		this.extensions = extensions;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		List<Extension> extensions = new ArrayList<>(BASE_EXTENSIONS);
		if (!CollectionUtils.isEmpty(this.extensions)) {
			extensions.addAll(this.extensions);
		}
		parser = Parser.builder().extensions(extensions).build();
		renderer = HtmlRenderer.builder().extensions(extensions).build();
	}

}
