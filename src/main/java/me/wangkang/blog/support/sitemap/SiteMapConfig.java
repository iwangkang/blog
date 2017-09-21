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

import java.text.DecimalFormat;

import me.wangkang.blog.core.exception.SystemException;

public class SiteMapConfig {

	private final Changefreq freq;
	private final float priority;

	public SiteMapConfig(Changefreq freq, float priority) {
		super();
		this.freq = freq;
		this.priority = priority;
	}

	public Changefreq getFreq() {
		return freq;
	}

	public String getFormattedPriority() {
		if (priority < 0 || priority > 1) {
			throw new SystemException("sitemap的priority必须在[0,1]之间，且只有一位小数");
		}
		return new DecimalFormat("#0.0").format(priority);
	}

}
