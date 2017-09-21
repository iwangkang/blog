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

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import me.wangkang.blog.core.dao.ArticleTagDao;
import me.wangkang.blog.core.dao.TagDao;
import me.wangkang.blog.core.entity.Tag;
import me.wangkang.blog.core.evt.ArticleIndexRebuildEvent;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.pageparam.PageResult;
import me.wangkang.blog.core.pageparam.TagQueryParam;
import me.wangkang.blog.core.service.ConfigService;
import me.wangkang.blog.core.service.TagService;

@Service
public class TagServiceImpl implements TagService, ApplicationEventPublisherAware {

	@Autowired
	private TagDao tagDao;
	@Autowired
	private ArticleTagDao articleTagDao;
	@Autowired
	private ConfigService configSerivce;
	@Autowired
	private ArticleIndexer articleIndexer;
	private ApplicationEventPublisher applicationEventPublisher;

	@Override
	@Transactional(readOnly = true)
	public PageResult<Tag> queryTag(TagQueryParam param) {
		param.setPageSize(configSerivce.getGlobalConfig().getTagPageSize());
		int count = tagDao.selectCount(param);
		List<Tag> datas = tagDao.selectPage(param);
		return new PageResult<>(param, count, datas);
	}

	@Override
	@CacheEvict(value = "hotTags", allEntries = true)
	@Sync
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public Tag updateTag(Tag tag, boolean merge) throws LogicException {
		Tag db = tagDao.selectById(tag.getId());
		if (db == null) {
			throw new LogicException("tag.notExists", "标签不存在");
		}
		if (db.getName().equals(tag.getName())) {
			return tag;
		}
		Tag newTag = tagDao.selectByName(tag.getName());
		if (newTag != null) {
			if (!merge) {
				throw new LogicException("tag.exists", "标签已经存在");
			}
			articleTagDao.merge(db, newTag);
			tagDao.deleteById(db.getId());
		} else {
			tagDao.update(tag);
		}

		Transactions.afterCommit(() -> {
			articleIndexer.removeTags(db.getName());
			articleIndexer.addTags(tag.getName());
			applicationEventPublisher.publishEvent(new ArticleIndexRebuildEvent(this));
		});

		return tag;
	}

	@Override
	@CacheEvict(value = "hotTags", allEntries = true)
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	public void deleteTag(Integer id) throws LogicException {
		Tag db = tagDao.selectById(id);
		if (db == null) {
			throw new LogicException("tag.notExists", "标签不存在");
		}
		articleTagDao.deleteByTag(db);
		tagDao.deleteById(id);

		Transactions.afterCommit(() -> {
			articleIndexer.removeTags(db.getName());
			applicationEventPublisher.publishEvent(new ArticleIndexRebuildEvent(this));
		});
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

}
