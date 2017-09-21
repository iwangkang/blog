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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.util.CollectionUtils;

import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.security.Environment;

/**
 * 锁辅助类
 * 
 * @author Administrator
 *
 */
public final class LockHelper {

	private static final String LOCKKEY_SESSION_KEY = "lockKeys";
	public static final String LAST_LOCK_SESSION_KEY = "lastLockResource";

	private LockHelper() {

	}

	/**
	 * 获取最后一次锁，如果锁不存在，抛出MissLockException
	 * 
	 * @param request
	 *            请求
	 * @return null 如果不存在或者空间不匹配
	 */
	public static LockBean getLockBean(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			LockBean lockBean = (LockBean) session.getAttribute(LAST_LOCK_SESSION_KEY);
			if (lockBean == null) {
				return null;
			}
			String alias = lockBean.getSpaceAlias();
			if (!Environment.match(alias == null ? null : new Space(alias))) {
				return null;
			}
			return lockBean;
		}
		return null;
	}

	/**
	 * 从请求中获取中获取资源id和钥匙集合
	 * 
	 * @param request
	 *            当前请求
	 * @return 资源id和钥匙的集合，可能为null
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, List<LockKey>> getKeysMap(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return null;
		}
		return (Map<String, List<LockKey>>) session.getAttribute(LOCKKEY_SESSION_KEY);
	}

	/**
	 * 在session中为对应锁增加钥匙
	 * 
	 * @param request
	 *            当前请求
	 * @param key
	 *            用户提供的钥匙
	 * @param resourceId
	 *            资源Id
	 */
	public static void addKey(HttpServletRequest request, LockKey key, LockResource lockResource) {
		Map<String, List<LockKey>> keysMap = (Map<String, List<LockKey>>) getKeysMap(request);
		if (keysMap == null) {
			keysMap = new HashMap<>();
		}
		List<LockKey> keys = keysMap.get(lockResource.getResourceId());
		if (CollectionUtils.isEmpty(keys)) {
			keys = new ArrayList<>(Arrays.asList(key));
			keysMap.put(lockResource.getResourceId(), keys);
		} else {
			keys.removeIf(_key -> _key.lockId().equals(key.lockId()));
			keys.add(key);
		}
		request.getSession().setAttribute(LOCKKEY_SESSION_KEY, keysMap);
	}

	/**
	 * 在session中存储解锁失败后的锁对象
	 * 
	 * @param request
	 *            当前请求
	 * @param lockBean
	 *            解锁失败后的所对象
	 */
	public static void storeLockBean(HttpServletRequest request, LockBean lockBean) {
		HttpSession session = request.getSession();
		session.setAttribute(LAST_LOCK_SESSION_KEY, lockBean);
	}

	/**
	 * 清除解锁失败后的锁对象
	 * 
	 * @param request
	 *            当前请求
	 */
	public static void clearLockBean(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.removeAttribute(LAST_LOCK_SESSION_KEY);
		}
	}
}
