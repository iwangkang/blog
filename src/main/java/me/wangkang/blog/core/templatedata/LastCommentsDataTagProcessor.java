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

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import me.wangkang.blog.core.entity.Comment;
import me.wangkang.blog.core.entity.CommentModule;
import me.wangkang.blog.core.entity.CommentModule.ModuleType;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.service.impl.CommentService;

public class LastCommentsDataTagProcessor extends DataTagProcessor<List<Comment>> {

	private static final Integer DEFAULT_LIMIT = 10;
	private static final int MAX_LIMIT = 50;

	@Autowired
	private CommentService commentService;

	public LastCommentsDataTagProcessor(String name, String dataName) {
		super(name, dataName);
	}

	@Override
	protected List<Comment> query(Attributes attributes) throws LogicException {
		ModuleType type = attributes.getEnum("moduleType", ModuleType.class, null);
		if (type == null) {
			return Collections.emptyList();
		}
		return commentService.queryLastComments(new CommentModule(type, attributes.getInteger("moduleId", null)),
				getLimit(attributes), attributes.getBoolean("queryAdmin", false));
	}

	private int getLimit(Attributes attributes) {
		int limit = attributes.getInteger("limit", 0);
		if (limit <= 0) {
			limit = DEFAULT_LIMIT;
		}
		if (limit > MAX_LIMIT) {
			limit = MAX_LIMIT;
		}
		return limit;
	}

}
