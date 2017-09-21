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

import java.util.List;
import java.util.Optional;

/**
 * 文件服务管理器
 * 
 * @author Administrator
 *
 */
public interface FileManager {

	/**
	 * 获取所有的文件服务
	 * 
	 * @return
	 */
	List<FileStore> getAllStores();
	
	/**
	 * 根据id查询文件服务
	 * 
	 * @param id
	 *            服务id
	 * @return
	 */
	Optional<FileStore> getFileStore(int id);

}
