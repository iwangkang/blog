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

import org.springframework.http.MediaType;
import org.springframework.transaction.TransactionStatus;

/**
 * 解析上下文
 * 
 * @author mhlx
 *
 */
public class ParseContext {

	private TransactionStatus transactionStatus;
	private ParseConfig config;
	private Template root;
	private MediaType mediaType;

	ParseContext() {
		super();
	}

	public TransactionStatus getTransactionStatus() {
		return transactionStatus;
	}

	public void setTransactionStatus(TransactionStatus transactionStatus) {
		this.transactionStatus = transactionStatus;
	}

	public ParseConfig getConfig() {
		return config;
	}

	public void setConfig(ParseConfig config) {
		this.config = config;
	}

	public Template getRoot() {
		return root;
	}

	public void setRoot(Template root) {
		this.root = root;
	}

	public MediaType getMediaType() {
		return mediaType;
	}

	public void setMediaType(MediaType mediaType) {
		this.mediaType = mediaType;
	}

	public boolean onlyCallable() {
		return config.isOnlyCallable();
	}
}
