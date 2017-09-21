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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;

import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.util.Resources;

public abstract class XmlRpcPingSupport extends PingService {

	private final String pingUrl;

	protected static final Logger LOGGER = LoggerFactory.getLogger(XmlRpcPingSupport.class);

	public XmlRpcPingSupport(String pingUrl) {
		super();
		this.pingUrl = pingUrl;
	}

	@Override
	public void ping(Article article, String blogName) throws Exception {
		String xml = articleToXml(article, blogName);
		LOGGER.debug("文章报文内容为:{}", xml);
		LOGGER.debug("开始向{}发送报文", pingUrl);
		String type = "text/html";
		String result = null;
		try {
			URL u = new URL(pingUrl);
			HttpURLConnection conn = (HttpURLConnection) u.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("User-Agent", "request");
			conn.setRequestProperty("Content-Type", type);
			conn.setRequestProperty("Content-Length", String.valueOf(xml.length()));
			try (OutputStream os = conn.getOutputStream();
					OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
				osw.write(xml);
			}
			// 获得响应状态
			int responseCode = conn.getResponseCode();

			if (HttpURLConnection.HTTP_OK == responseCode) {// 连接成功
				result = Resources.readResourceToString(new InputStreamResource(conn.getInputStream()));
			}
		} catch (IOException e) {
			String msg = "ping地址:" + pingUrl + "失败，文章访问地址为:" + urlHelper.getUrls().getUrl(article) + "，发送请求报文为" + xml
					+ "，失败信息:" + e.getMessage();
			throw new Exception(msg, e);
		}

		LOGGER.debug("响应信息为:" + result);

		if (result == null) {
			String msg = "ping地址:" + pingUrl + "失败，文章访问地址为:" + urlHelper.getUrls().getUrl(article) + "，发送请求报文为" + xml
					+ "，失败信息:无法获取服务器的响应信息";
			throw new Exception(msg);
		}
		boolean success = isSuccess(result);
		if (!success) {
			String msg = "ping地址:" + pingUrl + "失败，文章访问地址为:" + urlHelper.getUrls().getUrl(article) + "，发送请求报文为" + xml
					+ "，响应信息:" + result;
			throw new Exception(msg);
		}
	}

	/**
	 * 处理响应信息
	 * 
	 * @param result
	 * @return 是否成功
	 */
	protected abstract boolean isSuccess(String result);

	protected String articleToXml(Article article, String blogName) {
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		sb.append("<methodCall>");
		sb.append("<methodName>weblogUpdates.extendedPing</methodName>");
		sb.append("<params>");
		sb.append(" <param>");
		sb.append(" <value><string>" + blogName + "</string></value>");
		sb.append(" </param>");
		sb.append("<param>");
		String rootUrl = urlHelper.getUrls().getUrl(article.getSpace());
		sb.append("<value><string>").append(rootUrl).append("</string></value>");
		sb.append(" </param>");
		sb.append(" <param>");
		sb.append("  <value><string>").append(urlHelper.getUrls().getUrl(article)).append("</string></value>");
		sb.append(" </param>");
		sb.append(" <param>");
		sb.append("  <value><string>").append(rootUrl).append("/rss").append("</string></value>");
		sb.append("</param>");
		sb.append(" </params>");
		sb.append("</methodCall>");
		return sb.toString();
	}

}
