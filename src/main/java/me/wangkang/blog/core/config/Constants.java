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
package me.wangkang.blog.core.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.util.FileUtils;

/**
 * 系统常量
 * 
 * @author Administrator
 *
 */
public class Constants {

	/**
	 * session储存管理员的key
	 */
	public static final String USER_SESSION_KEY = "user";
	/**
	 * 默认编码
	 */
	public static final Charset CHARSET = StandardCharsets.UTF_8;
	/**
	 * session中储存最后一次认证失败的地址的key
	 */
	public static final String LAST_AUTHENCATION_FAIL_URL = "lastAuthencationFailUrl";
	/**
	 * 系统异常消息
	 */
	public static final Message SYSTEM_ERROR = new Message("error.system", "系统异常");
	/**
	 * 系统默认分页条数
	 */
	public static final int DEFAULT_PAGE_SIZE = 10;

	/**
	 * 系统配置文件存放目录
	 */
	public static final Path CONFIG_DIR = FileUtils.HOME_DIR.resolve("blog/config");

	/**
	 * dat文件存放目录
	 */
	public static final Path DAT_DIR = FileUtils.HOME_DIR.resolve("blog/dat");

	/**
	 * 成功
	 */
	public static final String ERROR = "error";

	/**
	 * 失败
	 */
	public static final String SUCCESS = "success";

	static {
		FileUtils.forceMkdir(CONFIG_DIR);
		FileUtils.forceMkdir(DAT_DIR);
	}

	private Constants() {

	}

}
