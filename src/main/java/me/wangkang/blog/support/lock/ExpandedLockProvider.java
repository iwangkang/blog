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
package me.wangkang.blog.support.lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;

import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.lock.Lock;
import me.wangkang.blog.util.Validators;

/**
 * 拓展锁提供器
 * 
 * @author Administrator
 *
 */
public class ExpandedLockProvider implements InitializingBean {

	private static final int MAX_ID_LENGTH = 20;
	private static final int MAX_TYPE_LENGTH = 20;
	private static final int MAX_NAME_LENGTH = 20;

	private List<Lock> expandedLocks = new ArrayList<>();

	private Map<String, List<Lock>> typesMap = new LinkedHashMap<>();
	private Map<String, Lock> idsMap = new LinkedHashMap<>();
	private Map<String, Resource> defaultTplResource = new LinkedHashMap<>();

	/**
	 * 根据id查询对应的锁
	 * 
	 * @param id
	 *            锁id
	 * @return 如果不存在返回null
	 */
	public Optional<Lock> findLock(String id) {
		return Optional.ofNullable(idsMap.get(id));
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (!CollectionUtils.isEmpty(expandedLocks)) {
			for (Lock lock : expandedLocks) {
				validLock(lock);
				String id = lock.getId();
				idsMap.put(id, lock);
				String type = lock.getLockType().trim();
				List<Lock> types = typesMap.get(type);
				if (types == null) {
					types = new ArrayList<>();
				}
				types.add(lock);
				typesMap.put(type, types);
			}
			for (String type : typesMap.keySet()) {
				if (defaultTplResource.get(type) == null) {
					throw new SystemException("锁类型" + type + "对应的基本模板没有被设置");
				}
			}
		}
	}

	private void validLock(Lock lock) {
		if (Validators.isEmptyOrNull(lock.getId(), true)) {
			throw new SystemException("锁ID不能为空");
		}
		String id = lock.getId().trim();
		if (id.length() > MAX_ID_LENGTH) {
			throw new SystemException("ID" + lock.getId() + "不能超过" + MAX_ID_LENGTH + "个字符");
		}
		if (!Validators.isLetterOrNum(id)) {
			throw new SystemException("ID只能包含英文字母和数字");
		}
		lock.setId(id);
		if (Validators.isEmptyOrNull(lock.getLockType(), true)) {
			throw new SystemException("锁类型不能为空");
		}
		String type = lock.getLockType().trim();
		if (type.length() > MAX_TYPE_LENGTH) {
			throw new SystemException("锁类型" + lock.getLockType() + "不能超过" + MAX_TYPE_LENGTH + "个字符");
		}
		if (!Validators.isLetterOrNum(type)) {
			throw new SystemException("锁类型只能包含英文字母和数字");
		}
		if (Validators.isEmptyOrNull(lock.getName(), true)) {
			throw new SystemException("锁名称不能为空");
		}
		String name = lock.getName().trim();
		if (name.length() > MAX_NAME_LENGTH) {
			throw new SystemException("锁名称" + lock.getName() + "不能超过" + MAX_NAME_LENGTH + "个字符");
		}
		lock.setName(name);
	}

	/**
	 * 获取所有的锁
	 * 
	 * @return 所有的锁
	 */
	public List<Lock> allLock() {
		return Collections.unmodifiableList(new ArrayList<>(idsMap.values()));
	}

	/**
	 * 获取所有的锁类型
	 * 
	 * @return 所有的锁类型
	 */
	public String[] getLockTypes() {
		return typesMap.keySet().toArray(new String[typesMap.size()]);
	}

	/**
	 * 检查锁类型是否存在
	 * 
	 * @param lockType
	 *            锁类型
	 * @return 是否存在
	 */
	public boolean checkLockTypeExists(String lockType) {
		return typesMap.containsKey(lockType);
	}

	/**
	 * 根据某个锁类型获取对应的默认模板
	 * 
	 * @param lockType
	 *            锁类型
	 * @return 模板资源
	 */
	public Resource getDefaultTemplateResource(String lockType) {
		return defaultTplResource.get(lockType);
	}

	public void setExpandedLocks(List<Lock> expandedLocks) {
		this.expandedLocks = expandedLocks;
	}

	public void setDefaultTplResource(Map<String, Resource> defaultTplResource) {
		this.defaultTplResource = defaultTplResource;
	}
}
