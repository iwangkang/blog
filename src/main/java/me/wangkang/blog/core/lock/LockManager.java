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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.support.lock.ExpandedLockProvider;
import me.wangkang.blog.util.Validators;

/**
 * 锁管理器
 * 
 * @author Administrator
 *
 */
@Component
public class LockManager implements InitializingBean {
	@Autowired
	private SysLockProvider sysLockProvider;
	@Autowired(required = false)
	private ExpandedLockProvider expandedLockProvider;

	private List<String> allTypes = new ArrayList<>();

	private static final Logger LOGGER = LoggerFactory.getLogger(LockManager.class);

	/**
	 * 解锁
	 * 
	 * @param lockResource
	 *            锁资源
	 * @throws LockException
	 *             解锁失败
	 */
	public void openLock(LockResource lockResource) throws LockException {
		Objects.requireNonNull(lockResource);
		if (Environment.isLogin()) {
			return;
		}
		Optional<String[]> optionalLockIds = lockResource.getLockIds();
		if (!optionalLockIds.isPresent()) {
			return;
		}
		String resourceId = lockResource.getResourceId();
		Arrays.stream(optionalLockIds.get()).forEach(lockId -> {
			findLock(lockId).ifPresent(lock -> {
				LockKey key = LockKeyContext.getKey(resourceId, lockId)
						.orElseThrow(() -> new LockException(lock, lockResource, null));
				try {
					lock.tryOpen(key);
				} catch (LogicException e) {
					LOGGER.debug("尝试用" + key.getKey() + "打开锁" + lock.getId() + "失败");
					throw new LockException(lock, lockResource, e.getLogicMessage());
				} catch (Exception e) {
					LOGGER.error("尝试用" + key.getKey() + "打开锁" + lock.getId() + "异常，异常信息:" + e.getMessage(), e);
					throw new LockException(lock, lockResource, Constants.SYSTEM_ERROR);
				}
			});
		});
	}

	/**
	 * 确保锁可用
	 * 
	 * @param lockId
	 *            锁id
	 * @throws LogicException
	 *             锁不可用(不存在)
	 */
	public void ensureLockvailable(String lockId) throws LogicException {
		if (lockId != null && !findLock(lockId).isPresent()) {
			throw new LogicException("lock.notexists", "锁不存在");
		}
	}

	/**
	 * 获取所有的锁
	 * 
	 * @return 所有的锁
	 */
	public List<Lock> allLock() {
		Map<String, Lock> idsMap = new LinkedHashMap<>();
		for (Lock lock : expandedLockProvider.allLock()) {
			idsMap.put(lock.getId(), lock);
		}
		for (Lock lock : sysLockProvider.allLock()) {
			if (!idsMap.containsKey(lock.getId())) {
				idsMap.put(lock.getId(), lock);
			}
		}
		return Collections.unmodifiableList(new ArrayList<>(idsMap.values()));
	}

	/**
	 * 获取所有的锁类型
	 * 
	 * @return
	 */
	public List<String> allTypes() {
		return allTypes;
	}

	/**
	 * 检查锁类型是否存在
	 * 
	 * @param lockType
	 *            锁类型
	 * @return 存在true，不存在false
	 */
	public boolean checkLockTypeExists(String lockType) {
		return expandedLockProvider.checkLockTypeExists(lockType) || sysLockProvider.checkLockTypeExists(lockType);
	}

	/**
	 * 根据锁类型获取默认模板
	 * 
	 * @param lockType
	 *            锁类型
	 * @return 模板资源
	 */
	public Resource getDefaultTemplateResource(String lockType) {
		Resource resource = expandedLockProvider.getDefaultTemplateResource(lockType);
		return resource == null ? sysLockProvider.getDefaultTemplateResource(lockType) : resource;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (expandedLockProvider == null) {
			expandedLockProvider = new ExpandedLockProvider();
		}
		Set<String> types = new LinkedHashSet<>();
		for (String type : expandedLockProvider.getLockTypes()) {
			if (!Validators.isLetterOrNum(type)) {
				throw new SystemException("锁类型只能为英文字母或者数字");
			}
			types.add(type);
		}
		for (String type : sysLockProvider.getLockTypes()) {
			types.add(type);
		}
		allTypes.addAll(types);
	}

	private Optional<Lock> findLock(String id) {
		return Optional.ofNullable(expandedLockProvider.findLock(id).orElse(sysLockProvider.findLock(id).orElse(null)));
	}

}
