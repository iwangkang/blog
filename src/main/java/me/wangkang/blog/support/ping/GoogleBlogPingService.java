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
package me.wangkang.blog.support.ping;

import me.wangkang.blog.util.StringUtils;

public class GoogleBlogPingService extends XmlRpcPingSupport {

	private static final String URL = "http://blogsearch.google.cn/ping/RPC2";

	public GoogleBlogPingService() {
		super(URL);
	}

	@Override
	protected boolean isSuccess(String result) {
		String[] flags = StringUtils.substringsBetween(result, "<boolean>", "</boolean>");
		if (flags.length == 0) {
			// not a xml response
			return false;
		}
		String flag = flags[0].trim();
		return "0".equals(flag);
	}

}
