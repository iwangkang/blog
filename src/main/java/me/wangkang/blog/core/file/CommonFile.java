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
package me.wangkang.blog.core.file;

import me.wangkang.blog.core.entity.BaseEntity;

/**
 * 系统存储的文件
 * 
 * @author Administrator
 *
 */
public class CommonFile extends BaseEntity {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private long size;// 文件大小，该大小仅仅是本服务上的文件大小，并不代表其他存储服务上的文件大小
	private String extension;// 后缀名
	private int store;// 文件存储器
	private String originalFilename;// 原始文件名

	private Integer width;// 图片等文件
	private Integer height;// 图片等文件

	/**
	 * default
	 */
	public CommonFile() {
		super();
	}

	public CommonFile(CommonFile cf) {
		this.size = cf.size;
		this.extension = cf.extension;
		this.store = cf.store;
		this.id = cf.id;
		this.originalFilename = cf.originalFilename;
		this.width = cf.width;
		this.height = cf.height;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public String getExtension() {
		return extension;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public void setOriginalFilename(String originalFilename) {
		this.originalFilename = originalFilename;
	}

	public Integer getWidth() {
		return width;
	}

	public void setWidth(Integer width) {
		this.width = width;
	}

	public Integer getHeight() {
		return height;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	public int getStore() {
		return store;
	}

	public void setStore(int store) {
		this.store = store;
	}
}
