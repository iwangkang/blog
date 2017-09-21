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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.util.CollectionUtils;

import me.wangkang.blog.core.exception.SystemException;

/**
 * 默认文件存储管理器
 * 
 * @author Administrator
 *
 */
public class DefaultFileManager implements FileManager, InitializingBean {

	private List<FileStore> stores = new ArrayList<>();
	private Map<Integer, FileStore> storeMap = new HashMap<>();

	@Override
	public Optional<FileStore> getFileStore(int id) {
		return Optional.ofNullable(storeMap.get(id));
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (!CollectionUtils.isEmpty(stores)) {
			storeMap = stores.stream().collect(Collectors.toMap(FileStore::id, store -> store));
		}
	}

	@Override
	public List<FileStore> getAllStores() {
		return Collections.unmodifiableList(stores);
	}

	public void setStores(List<FileStore> stores) {
		this.stores = stores;
	}

	@EventListener
	public void handleFileServiceRegister(FileStoreRegisterEvent evt) {
		FileStore fs = evt.getFileStore();
		int id = fs.id();
		if (storeMap.containsKey(id)) {
			throw new SystemException("已经存在id为" + id + "的存储器了");
		}
		stores.add(fs);
		storeMap.put(id, fs);
	}
}
