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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import me.wangkang.blog.core.evt.LockDeleteEvent;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.lock.SysLock.SysLockType;
import me.wangkang.blog.core.security.BCrypts;
import me.wangkang.blog.util.StringUtils;

/**
 * 系统锁管理
 * 
 * @author Administrator
 *
 */
@Component
public class SysLockProvider implements ApplicationEventPublisherAware {

	@Autowired
	private SysLockDao sysLockDao;

	private ApplicationEventPublisher applicationEventPublisher;

	private static final String[] LOCK_TYPES = { SysLockType.PASSWORD.name(), SysLockType.QA.name() };

	/**
	 * 删除锁
	 * 
	 * @param id
	 *            锁id
	 */
	@Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRED)
	@CacheEvict(value = "lockCache", key = "'lock-'+#id")
	public void removeLock(String id) {
		sysLockDao.delete(id);
		applicationEventPublisher.publishEvent(new LockDeleteEvent(this, id));
	}

	/**
	 * 根据id查找锁
	 * 
	 * @param id
	 *            锁id
	 * @return 如果不存在返回null
	 */
	@Transactional(readOnly = true)
	@Cacheable(value = "lockCache", key = "'lock-'+#id", unless = "#result == null")
	public Optional<SysLock> findLock(String id) {
		return Optional.ofNullable(sysLockDao.selectById(id));
	}

	/**
	 * 获取所有的系统锁
	 * 
	 * @return 所有的锁
	 */
	@Transactional(readOnly = true)
	public List<SysLock> allLock() {
		return sysLockDao.selectAll();
	}

	/**
	 * 新增系统锁
	 * 
	 * @param lock
	 *            待新增的系统锁
	 */
	@Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRED)
	public SysLock addLock(SysLock lock) {
		lock.setId(StringUtils.uuid());
		lock.setCreateDate(Timestamp.valueOf(LocalDateTime.now()));
		encryptPasswordLock(lock);
		sysLockDao.insert(lock);
		return lock;
	}

	/**
	 * 更新系统锁
	 * 
	 * @param lock
	 *            待更新的锁
	 * @throws LogicException
	 *             逻辑异常：锁不存在|锁类型不匹配
	 */
	@Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRED)
	@CacheEvict(value = "lockCache", key = "'lock-'+#lock.id")
	public SysLock updateLock(SysLock lock) throws LogicException {
		SysLock db = sysLockDao.selectById(lock.getId());
		if (db == null) {
			throw new LogicException("lock.notexists", "锁不存在，可能已经被删除");
		}
		if (!db.getType().equals(lock.getType())) {
			throw new LogicException("lock.type.unmatch", "锁类型不匹配");
		}
		encryptPasswordLock(lock);
		sysLockDao.update(lock);
		return lock;
	}

	/**
	 * 获取所有的系统锁类型
	 * 
	 * @return 所有的锁类型
	 */
	public String[] getLockTypes() {
		return LOCK_TYPES;
	}

	/**
	 * 检查目标锁类型是否存在
	 * 
	 * @param lockType
	 *            锁类型
	 * @return 存在：true，不存在：false
	 */
	public boolean checkLockTypeExists(String lockType) {
		if (lockType != null) {
			for (String _lockType : LOCK_TYPES) {
				if (lockType.equals(_lockType)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 根据锁类型获取默认的模板资源
	 * 
	 * @param lockType
	 *            锁类型
	 * @return 模板资源
	 */
	public Resource getDefaultTemplateResource(String lockType) {
		return new ClassPathResource("resources/page/LOCK_" + lockType + ".html");
	}

	private void encryptPasswordLock(SysLock lock) {
		if (SysLockType.PASSWORD.equals(lock.getType())) {
			PasswordLock plock = (PasswordLock) lock;
			plock.setPassword(BCrypts.encode(plock.getPassword()));
		}
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}
}
