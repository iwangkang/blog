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

/**
 * xml format :
 * 
 * <pre>
 * {@code 
<?xml version="1.0" encoding="UTF-8"?>
<methodCall>
    <methodName>weblogUpdates.extendedPing</methodName>
    <params>
        <param>
            <value><string>百度的空间</string></value>
        </param>
        <param>
            <value><string>http://hi.baidu.com/baidu/</string></value>
        </param>
        <param>
            <value><string>http://baidu.com/blog/example.html</string></value>
        </param>
        <param>
            <value><string>http://hi.baidu.com/baidu/rss</string></value>
        </param>
    </params>
</methodCall>
}
 * </pre>
 * 
 * 
 * {@link http://zhanzhang.baidu.com/tools/ping}
 * 
 * @author Administrator
 *
 */
public class BaiduBlogPingService extends XmlRpcPingSupport {

	private static final String URL = "http://ping.baidu.com/ping/RPC2";

	public BaiduBlogPingService() {
		super(URL);
	}

	/**
	 * <pre>
	 * {@code
	 * <?xml version="1.0" encoding="UTF-8"?>
	<methodResponse>
	<params>
	    <param>
	        <value>
	            <int>1</int>
	        </value>
	    </param>
	</params>
	</methodResponse>
	 * }
	 * </pre>
	 */
	@Override
	protected boolean isSuccess(String result) {
		String[] flags = StringUtils.substringsBetween(result, "<int>", "</int>");
		if (flags.length == 0) {
			// not a xml response
			return false;
		}
		String flag = flags[0].trim();
		return "0".equals(flag);
	}
}
