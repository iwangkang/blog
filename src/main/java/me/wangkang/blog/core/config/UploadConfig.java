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

/**
 * 指定路径上传
 * 
 * @author mhlx
 *
 */
public class UploadConfig {

	/**
	 * path 以 FileService.splitChar分隔，代表文件夹路径<br>
	 * 例如/a/b/c/d，如果为空，则代表上传到根目录
	 */
	private String path;

	/**
	 * 存储服务，不能为空
	 */
	private Integer store;

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Integer getStore() {
		return store;
	}

	public void setStore(Integer store) {
		this.store = store;
	}

}
