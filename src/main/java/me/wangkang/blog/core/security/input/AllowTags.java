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

/**
 * 允许的标签
 * 
 * @author Administrator
 *
 */
public class AllowTags {

	private List<Tag> tags = new ArrayList<>();

	public void addSimpleTags(String[] simpleTags) {
		if (simpleTags != null && simpleTags.length > 0) {
			Arrays.stream(simpleTags).filter(name -> !name.isEmpty()).distinct().map(Tag::new).forEach(tags::add);
		}
	}

	public List<Tag> getTags() {
		return tags;
	}

	public void setTags(List<Tag> tags) {
		this.tags = tags;
	}

	/**
	 * 添加允许的标签
	 * 
	 * @param tag
	 */
	public void addTag(Tag tag) {
		this.tags.add(tag);
	}

}
