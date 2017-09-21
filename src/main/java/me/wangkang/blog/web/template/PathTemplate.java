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
import java.nio.file.Path;
import java.util.Objects;

import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.Webs;

public class PathTemplate implements Template {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final String PATH_PREFIX = TEMPLATE_PREFIX + "Path" + SPLITER;

	private transient final Path associate;
	private final boolean registrable;
	private final String relativePath;
	private final boolean pub;
	private String template;
	private String templateName;

	public PathTemplate(Path associate, boolean registrable, String relativePath, boolean pub) {
		super();
		this.associate = associate;
		this.relativePath = relativePath;
		this.registrable = registrable;
		this.pub = pub;
	}

	public PathTemplate(PathTemplate clone) {
		super();
		this.associate = clone.associate;
		this.registrable = clone.registrable;
		this.relativePath = clone.relativePath;
		this.pub = clone.pub;
	}

	public Path getAssociate() {
		return associate;
	}

	public boolean isRegistrable() {
		return registrable;
	}

	@Override
	public boolean isRoot() {
		return registrable;
	}

	@Override
	public String getTemplate() {
		if (template == null) {
			try {
				template = FileUtils.toString(associate);
			} catch (IOException e) {
				template = "";
			}
		}
		return template;
	}

	@Override
	public String getTemplateName() {
		if (templateName == null) {
			templateName = getTemplateName(relativePath);
		}
		return templateName;
	}

	public static String getTemplateName(String relativePath) {
		String path;
		String spaceAlias = null;
		if (relativePath.indexOf('/') == -1) {
			path = relativePath;
		} else {
			spaceAlias = Webs.getSpaceFromPath(relativePath);
			if (spaceAlias != null) {
				path = relativePath.substring(spaceAlias.length() + 6);
			} else {
				path = relativePath;
			}
		}
		return getTemplateName(path, spaceAlias);
	}

	public static String getTemplateName(String path, String spaceAlias) {
		String templateName = PATH_PREFIX + FileUtils.cleanPath(path);
		if (spaceAlias != null) {
			templateName += SPLITER + spaceAlias;
		}
		return templateName;
	}

	@Override
	public Template cloneTemplate() {
		return new PathTemplate(this);
	}

	@Override
	public boolean isCallable() {
		return false;
	}

	public String getRelativePath() {
		return relativePath;
	}

	public boolean isPub() {
		return pub;
	}

	@Override
	public boolean equalsTo(Template other) {
		if (Validators.baseEquals(this, other)) {
			PathTemplate rhs = (PathTemplate) other;
			return Objects.equals(this.getTemplate(), rhs.getTemplate())
					&& Objects.equals(this.getTemplateName(), rhs.getTemplateName())
					&& Objects.equals(this.registrable, rhs.registrable);
		}
		return false;
	}

	public static boolean isPathTemplate(String templateName) {
		return templateName != null && templateName.startsWith(PATH_PREFIX);
	}
}
