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

import org.springframework.context.ApplicationEvent;

/**
 * 这个类用来向文件存储服务中注册一个文件存储器
 * 
 * @author Administrator
 * @see DefaultFileManager
 */
public class FileStoreRegisterEvent extends ApplicationEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private transient final FileStore fileStore;

	public FileStoreRegisterEvent(Object source, FileStore fileStore) {
		super(source);
		this.fileStore = fileStore;
	}

	public FileStore getFileStore() {
		return fileStore;
	}

}
