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
package me.wangkang.blog.core.service;

import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.vo.ArticleStatistics;
import me.wangkang.blog.core.vo.CommentStatistics;
import me.wangkang.blog.core.vo.StatisticsDetail;
import me.wangkang.blog.core.vo.TagStatistics;

public interface StatisticsService {

	/**
	 * 查询<b>当前空间</b>下文章统计情况
	 * <p>
	 * <b>用于DataTag</b>
	 * </p>
	 * 
	 * @return
	 * @throws LogicException
	 */
	ArticleStatistics queryArticleStatistics();

	/**
	 * 查询<b>当前空间</b>标签统计情况
	 * <p>
	 * <b>用于DataTag</b>
	 * </p>
	 * 
	 * @return
	 * @throws LogicException
	 */
	TagStatistics queryTagStatistics();

	/**
	 * 查询<b>当前空间</b>评论统计情况
	 * <p>
	 * <b>用于DataTag</b>
	 * </p>
	 * 
	 * @return
	 * @throws LogicException
	 */
	CommentStatistics queryCommentStatistics();

	/**
	 * 查询统计详情
	 * <p>
	 * <b>用于管理台统计</b>
	 * </p>
	 * 
	 * @param spaceId
	 *            空间id，如果为空，查询全部
	 * @return
	 * @throws LogicException
	 *             空间不存在
	 */
	StatisticsDetail queryStatisticsDetail(Integer spaceId) throws LogicException;

}
