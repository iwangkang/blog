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
package me.wangkang.blog.web.validator;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import me.wangkang.blog.core.config.CommentConfig;

@Component
public class CommentConfigValidator implements Validator {

	private static final int[] LIMIT_SECOND_RANGE = { 1, 300 };
	private static final int[] LIMIT_COUNT_RANGE = { 1, 100 };

	private static final int[] COMMENT_PAGE_SIZE_RANGE = { 1, 50 };

	@Override
	public boolean supports(Class<?> clazz) {
		return CommentConfig.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		CommentConfig config = (CommentConfig) target;
		if (config.getEditor() == null) {
			errors.reject("commentConfig.editor.blank", "评论编辑器不能为空");
			return;
		}
		if (config.getCheck() == null) {
			errors.reject("commentConfig.check.blank", "评论审核不能为空");
			return;
		}
		Integer limitCount = config.getLimitCount();
		if (limitCount == null) {
			errors.reject("commentConfig.limitCount.blank", "限制评论数不能为空");
			return;
		}
		if (limitCount < LIMIT_COUNT_RANGE[0] || limitCount > LIMIT_COUNT_RANGE[1]) {
			errors.reject("commentConfig.limitCount.invalid",
					new Object[] { LIMIT_COUNT_RANGE[0], LIMIT_COUNT_RANGE[1] },
					"限制评论数应该在" + LIMIT_COUNT_RANGE[0] + "和" + LIMIT_COUNT_RANGE[1] + "之间");
			return;
		}

		Integer limitSec = config.getLimitSec();
		if (limitSec == null) {
			errors.reject("commentConfig.limitSec.blank", "限制评论时间不能为空");
			return;
		}
		if (limitSec < LIMIT_SECOND_RANGE[0] || limitSec > LIMIT_SECOND_RANGE[1]) {
			errors.reject("commentConfig.limitSec.invalid",
					new Object[] { LIMIT_SECOND_RANGE[0], LIMIT_SECOND_RANGE[1] },
					"限制评论时间应该在" + LIMIT_SECOND_RANGE[0] + "和" + LIMIT_SECOND_RANGE[1] + "之间");
			return;
		}

		Integer pageSize = config.getPageSize();

		if (pageSize == null) {
			errors.reject("commentConfig.pagesize.blank", "评论每页显示数目不能为空");
			return;
		}

		if (pageSize < COMMENT_PAGE_SIZE_RANGE[0]) {
			errors.reject("commentConfig.pagesize.toosmall", new Object[] { COMMENT_PAGE_SIZE_RANGE[0] },
					"评论每页数量不能小于" + COMMENT_PAGE_SIZE_RANGE[0]);
			return;
		}

		if (pageSize > COMMENT_PAGE_SIZE_RANGE[1]) {
			errors.reject("commentConfig.pagesize.toobig", new Object[] { COMMENT_PAGE_SIZE_RANGE[1] },
					"评论每页数量不能大于" + COMMENT_PAGE_SIZE_RANGE[1]);
			return;
		}

	}

}
