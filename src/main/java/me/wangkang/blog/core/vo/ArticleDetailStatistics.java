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

import java.util.EnumMap;
import java.util.Map;

import me.wangkang.blog.core.entity.Article.ArticleStatus;

public class ArticleDetailStatistics extends ArticleStatistics {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Map<ArticleStatus, Integer> statusCountMap = new EnumMap<>(ArticleStatus.class);

	public ArticleDetailStatistics(ArticleStatistics statistics) {
		super(statistics);
	}

	public Map<ArticleStatus, Integer> getStatusCountMap() {
		return statusCountMap;
	}

	public void setStatusCountMap(Map<ArticleStatus, Integer> statusCountMap) {
		this.statusCountMap = statusCountMap;
	}
}