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
package me.wangkang.blog.core.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import me.wangkang.blog.core.dao.SpaceDao;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.evt.ArticleIndexRebuildEvent;
import me.wangkang.blog.core.evt.LockDeleteEvent;
import me.wangkang.blog.core.evt.SpaceDeleteEvent;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.lock.LockManager;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.pageparam.SpaceQueryParam;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.SpaceService;

@Service
public class SpaceServiceImpl implements SpaceService, ApplicationEventPublisherAware {

	@Autowired
	private SpaceDao spaceDao;
	@Autowired
	private LockManager lockManager;
	@Autowired
	private SpaceCache spaceCache;
	private ApplicationEventPublisher applicationEventPublisher;

	@Override
	@Sync
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Space addSpace(Space space) throws LogicException {
		lockManager.ensureLockvailable(space.getLockId());

		if (spaceDao.selectByAlias(space.getAlias()) != null) {
			throw new LogicException(
					new Message("space.alias.exists", "别名为" + space.getAlias() + "的空间已经存在了", space.getAlias()));
		}
		if (spaceDao.selectByName(space.getName()) != null) {
			throw new LogicException(
					new Message("space.name.exists", "名称为" + space.getName() + "的空间已经存在了", space.getName()));
		}
		space.setCreateDate(Timestamp.valueOf(LocalDateTime.now()));
		if (space.getIsDefault()) {
			spaceDao.resetDefault();
		}
		spaceDao.insert(space);
		spaceCache.init();

		return space;
	}

	@Override
	@Caching(evict = { @CacheEvict(value = "articleCache", allEntries = true),
			@CacheEvict(value = "articleFilesCache", allEntries = true) })
	@Sync
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Space updateSpace(Space space) throws LogicException {
		Space db = spaceCache.getSpace(space.getId()).orElseThrow(() -> new LogicException("space.notExists", "空间不存在"));
		Space nameDb = spaceDao.selectByName(space.getName());
		if (nameDb != null && !nameDb.equals(db)) {
			throw new LogicException(
					new Message("space.name.exists", "名称为" + space.getName() + "的空间已经存在了", space.getName()));
		}
		// 如果空间是私有的，那么无法加锁
		if (space.getIsPrivate()) {
			space.setLockId(null);
		} else {
			lockManager.ensureLockvailable(space.getLockId());
		}

		if (space.getIsDefault()) {
			spaceDao.resetDefault();
		}

		spaceDao.update(space);
		spaceCache.init();
		Transactions.afterCommit(() -> applicationEventPublisher.publishEvent(new ArticleIndexRebuildEvent(this)));
		return space;
	}

	@Override
	@Sync
	@Caching(evict = { @CacheEvict(value = "articleCache", allEntries = true),
			@CacheEvict(value = "articleFilesCache", allEntries = true) })
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class, isolation = Isolation.SERIALIZABLE)
	public void deleteSpace(Integer id) throws LogicException {
		Space space = spaceCache.getSpace(id).orElseThrow(() -> new LogicException("space.notExists", "空间不存在"));
		if (space.getIsDefault()) {
			throw new LogicException("space.default.canNotDelete", "默认空间不能被删除");
		}
		// 推送空间删除事件，通知文章等删除
		this.applicationEventPublisher.publishEvent(new SpaceDeleteEvent(this, space));

		spaceDao.deleteById(id);

		spaceCache.init();
		Transactions.afterCommit(() -> applicationEventPublisher.publishEvent(new ArticleIndexRebuildEvent(this)));
	}

	@Override
	public Optional<Space> getSpace(Integer id) {
		return spaceCache.getSpace(id);
	}

	@Override
	public Optional<Space> getSpace(String alias) {
		return spaceCache.getSpace(alias);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Space> querySpace(SpaceQueryParam param) {
		if (param.getQueryPrivate() && !Environment.isLogin()) {
			param.setQueryPrivate(false);
		}
		return spaceCache.getSpaces(param.getQueryPrivate());
	}

	@EventListener
	public void handleLockDeleteEvent(LockDeleteEvent event) {
		spaceDao.deleteLock(event.getLockId());
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}
}
