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

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import me.wangkang.blog.core.entity.Space;

public interface CommentServer {

	/**
	 * 查询文章列表评论数
	 * 
	 * @param ids
	 *            文章ids
	 * @return 文章id和评论数的map
	 */
	Map<Integer, Integer> queryArticlesCommentCount(List<Integer> ids);

	/**
	 * 查询文章评论数
	 * 
	 * @param id
	 * @return
	 */
	OptionalInt queryArticleCommentCount(Integer id);

	/**
	 * 查询某个空间下所有文章的评论总数
	 * 
	 * @param space
	 *            空间，如果为空，查询全部
	 * @return
	 */
	int queryArticlesTotalCommentCount(Space space, boolean queryPrivate);

	/**
	 * 查询某个空间下自定义页面的评论总数
	 * 
	 * @param space
	 *            空间，如果为空，查询全部
	 * @return
	 */
	int queryPagesTotalCommentCount(Space space, boolean queryPrivate);

}
