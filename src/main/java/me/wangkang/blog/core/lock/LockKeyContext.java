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
package me.wangkang.blog.core.lock;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.CollectionUtils;

/**
 * 钥匙上下文
 * 
 * @author Administrator
 *
 */
public class LockKeyContext {

	private static final ThreadLocal<Map<String, List<LockKey>>> KEYS_LOCAL = new ThreadLocal<>();

	private LockKeyContext() {
		super();
	}

	/**
	 * 从上下文中获取钥匙
	 * 
	 * @return 如果不存在返回null
	 */
	public static Optional<LockKey> getKey(String resourceId, String lockId) {
		Map<String, List<LockKey>> keyMap = KEYS_LOCAL.get();
		if (CollectionUtils.isEmpty(keyMap)) {
			return Optional.empty();
		}
		List<LockKey> resourceKeys = keyMap.get(resourceId);
		return CollectionUtils.isEmpty(resourceKeys) ? Optional.empty()
				: resourceKeys.stream().filter(key -> key.lockId().equals(lockId)).findAny();
	}

	/**
	 * 清理上下文
	 */
	public static void remove() {
		KEYS_LOCAL.remove();
	}

	/**
	 * 设置上下文
	 * 
	 * @param keysMap
	 */
	public static void set(Map<String, List<LockKey>> keysMap) {
		KEYS_LOCAL.set(keysMap);
	}

}
