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
package me.wangkang.blog.core.templatedata;

import org.springframework.beans.factory.annotation.Autowired;

import me.wangkang.blog.core.entity.CommentMode;
import me.wangkang.blog.core.entity.CommentModule;
import me.wangkang.blog.core.entity.Comment.CommentStatus;
import me.wangkang.blog.core.entity.CommentModule.ModuleType;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.pageparam.CommentQueryParam;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.impl.CommentService;
import me.wangkang.blog.core.vo.CommentPageResult;

public class CommentsDataTagProcessor extends DataTagProcessor<CommentPageResult> {
	@Autowired
	private CommentService commentService;

	public CommentsDataTagProcessor(String name, String dataName) {
		super(name, dataName);
	}

	@Override
	protected CommentPageResult query(Attributes attributes) throws LogicException {
		CommentQueryParam param = new CommentQueryParam();

		String moduleTypeStr = attributes.get("moduleType");
		String moduleIdStr = attributes.get("moduleId");
		if (moduleIdStr != null && moduleTypeStr != null) {
			try {
				param.setModule(new CommentModule(ModuleType.valueOf(moduleTypeStr.toUpperCase()),
						Integer.parseInt(moduleIdStr)));
			} catch (Exception e) {
				LOGGER.debug(e.getMessage(), e);
			}
		}

		param.setMode(attributes.getEnum("mode", CommentMode.class, CommentMode.LIST));
		param.setAsc(attributes.getBoolean("asc", true));
		param.setCurrentPage(attributes.getInteger("currentPage", 0));
		param.setPageSize(attributes.getInteger("pageSize", 0));

		if (param.getCurrentPage() < 0) {
			param.setCurrentPage(0);
		}

		param.setStatus(!Environment.isLogin() ? CommentStatus.NORMAL : null);

		return commentService.queryComment(param);
	}

}
