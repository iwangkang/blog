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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import me.wangkang.blog.core.dao.SpaceDao;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.pageparam.SpaceQueryParam;

@Component
public class SpaceCache implements InitializingBean {
	@Autowired
	private SpaceDao spaceDao;

	private final List<Space> cache = new ArrayList<>();
	private final StampedLock lock = new StampedLock();

	@Autowired
	private PlatformTransactionManager platformTransactionManager;

	public List<Space> getSpaces(boolean queryPrivate) {
		long stamp = lock.tryOptimisticRead();
		List<Space> result = doQuery(queryPrivate);
		if (!lock.validate(stamp)) {
			stamp = lock.readLock();
			try {
				result = doQuery(queryPrivate);
			} finally {
				lock.unlockRead(stamp);
			}
		}
		return result;
	}

	/**
	 * 根据别名查询空间
	 * 
	 * @param alias
	 * @return
	 */
	public Optional<Space> getSpace(String alias) {
		long stamp = lock.tryOptimisticRead();
		Optional<Space> result = queryByAlias(alias);
		if (!lock.validate(stamp)) {
			stamp = lock.readLock();
			try {
				result = queryByAlias(alias);
			} finally {
				lock.unlockRead(stamp);
			}
		}
		return result;
	}

	/**
	 * 根据id查询空间
	 * 
	 * @param id
	 * @return
	 */
	public Optional<Space> getSpace(Integer id) {
		long stamp = lock.tryOptimisticRead();
		Optional<Space> result = queryById(id);
		if (!lock.validate(stamp)) {
			stamp = lock.readLock();
			try {
				result = queryById(id);
			} finally {
				lock.unlockRead(stamp);
			}
		}
		return result;
	}

	/**
	 * 判断空间是否存在
	 * 
	 * @param spaceId
	 *            空间ID
	 * @return
	 * @throws LogicException
	 */
	public Space checkSpace(Integer spaceId) throws LogicException {
		if (spaceId == null) {
			return null;
		}
		return getSpace(spaceId).orElseThrow(() -> new LogicException("space.notExists", "空间不存在"));
	}

	/**
	 * 重新查询所有空间并载入内存
	 */
	public void init() {

		Transactions.afterCommit(() -> Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
			long stamp = lock.writeLock();
			try {
				List<Space> spaces = spaceDao.selectByParam(new SpaceQueryParam());
				cache.clear();
				cache.addAll(spaces);
			} finally {
				lock.unlockWrite(stamp);
			}
		}));

	}

	private List<Space> doQuery(boolean queryPrivate) {
		Stream<Space> stream = cache.stream();
		if (!queryPrivate) {
			stream = stream.filter(space -> !space.getIsPrivate());
		}
		return stream.map(Space::new).collect(Collectors.toList());
	}

	private Optional<Space> queryByAlias(String alias) {
		return cache.stream().filter(space -> Objects.equals(alias, space.getAlias())).map(Space::new).findAny();
	}

	private Optional<Space> queryById(Integer id) {
		return cache.stream().filter(space -> Objects.equals(id, space.getId())).map(Space::new).findAny();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		cache.addAll(spaceDao.selectByParam(new SpaceQueryParam()));
	}

}
