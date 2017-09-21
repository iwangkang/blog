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
package me.wangkang.blog.core.vo;

import me.wangkang.blog.web.template.PathTemplate;

public class PathTemplateBean {
	private String tpl;
	private String path;
	private boolean registrable;
	private boolean pub;

	public PathTemplateBean() {
		super();
	}

	public PathTemplateBean(String path) {
		super();
		this.path = path;
	}

	public PathTemplateBean(PathTemplate template) {
		this.tpl = template.getTemplate();
		this.path = template.getRelativePath();
		this.registrable = template.isRegistrable();
		this.pub = template.isPub();
	}

	public String getTpl() {
		return tpl;
	}

	public void setTpl(String tpl) {
		this.tpl = tpl;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean isRegistrable() {
		return registrable;
	}

	public void setRegistrable(boolean registrable) {
		this.registrable = registrable;
	}

	public boolean isPub() {
		return pub;
	}

	public void setPub(boolean pub) {
		this.pub = pub;
	}

}
