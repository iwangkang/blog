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
package me.wangkang.blog.web.template;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Objects;

import org.springframework.core.io.Resource;

import me.wangkang.blog.core.entity.BaseEntity;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.util.Resources;
import me.wangkang.blog.util.Times;
import me.wangkang.blog.util.Validators;

/**
 * 片段，用来展现数据
 * 
 * @see DataTagProcessor
 * @author Administrator
 *
 */
public class Fragment extends BaseEntity implements Template {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String FRAGMENT_PREFIX = TEMPLATE_PREFIX + "Fragment" + SPLITER;

	private String name;
	private String tpl;
	private Space space;

	private String description;
	private Timestamp createDate;
	private boolean global;

	/**
	 * 是否能够被ajax调用
	 */
	private boolean callable;

	private String templateName;

	public Fragment() {
		super();
	}

	public Fragment(String name, Space space) {
		super();
		this.name = name;
		this.space = space;
	}

	public Fragment(String name) {
		super();
		this.name = name;
	}

	public Fragment(String name, Resource resource, boolean callable) throws IOException {
		this.name = name;
		this.tpl = Resources.readResourceToString(resource);
		this.callable = callable;
		this.createDate = Timestamp.valueOf(Times.now());
	}

	public Fragment(String name, Resource resource) throws IOException {
		this(name, resource, false);
	}

	public Fragment(Fragment fragment) {
		this.name = fragment.name;
		this.tpl = fragment.tpl;
		this.callable = fragment.callable;
		this.space = fragment.space;
		this.id = fragment.id;
		this.description = fragment.description;
		this.createDate = fragment.createDate;
		this.global = fragment.global;
	}

	public String getTpl() {
		return tpl;
	}

	public void setTpl(String tpl) {
		this.tpl = tpl;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public boolean isCallable() {
		return callable;
	}

	public void setCallable(boolean callable) {
		this.callable = callable;
	}

	public Space getSpace() {
		return space;
	}

	public void setSpace(Space space) {
		this.space = space;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name);
	}

	@Override
	public boolean equals(Object obj) {
		if (Validators.baseEquals(this, obj)) {
			Fragment other = (Fragment) obj;
			return Objects.equals(this.name, other.name);
		}
		return false;
	}

	public final Fragment toExportFragment() {
		Fragment f = new Fragment();
		f.setName(name);
		f.setTpl(tpl);
		f.setDescription("");
		f.setCallable(callable);
		f.setGlobal(global);
		return f;
	}

	@Override
	public final boolean isRoot() {
		return false;
	}

	@Override
	public final String getTemplate() {
		return tpl;
	}

	@Override
	public String getTemplateName() {
		if (templateName == null) {
			templateName = getTemplateName(name, space);
		}
		return templateName;
	}

	public static String getTemplateName(String name, Space space) {
		StringBuilder sb = new StringBuilder();
		sb.append(FRAGMENT_PREFIX).append(name);
		if (space != null && space.hasId()) {
			sb.append(SPLITER).append(space.getId());
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return "Fragment [name=" + name + ",  space=" + space + ", callable=" + callable + "]";
	}

	@Override
	public Template cloneTemplate() {
		return new Fragment(this);
	}

	@Override
	public boolean equalsTo(Template other) {
		if (Validators.baseEquals(this, other)) {
			Fragment rhs = (Fragment) other;
			return Objects.equals(this.callable, rhs.callable) && Objects.equals(this.name, rhs.name)
					&& Objects.equals(this.space, rhs.space) && Objects.equals(this.tpl, rhs.tpl)
					&& Objects.equals(this.createDate, rhs.createDate)
					&& Objects.equals(this.description, rhs.description) && Objects.equals(this.global, rhs.global)
					&& Objects.equals(this.id, rhs.id);
		}
		return false;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Timestamp getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Timestamp createDate) {
		this.createDate = createDate;
	}

	public boolean isGlobal() {
		return global;
	}

	public void setGlobal(boolean global) {
		this.global = global;
	}

	public static boolean isFragmentTemplate(String templateName) {
		return templateName != null && templateName.startsWith(FRAGMENT_PREFIX);
	}
}
