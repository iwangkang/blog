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

import java.io.Serializable;

import me.wangkang.blog.core.message.Message;

/**
 * 导入纪录
 * 
 * @author Administrator
 *
 */
public class ImportRecord implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final boolean success;
	private final Message message;

	public ImportRecord(boolean success, Message message) {
		super();
		this.success = success;
		this.message = message;
	}

	public boolean isSuccess() {
		return success;
	}

	public Message getMessage() {
		return message;
	}

}