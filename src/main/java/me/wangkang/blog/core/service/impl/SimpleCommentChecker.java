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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import me.wangkang.blog.core.config.CommentConfig;
import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.entity.Comment;
import me.wangkang.blog.core.entity.User;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.UserQueryService;
import me.wangkang.blog.util.Validators;

public class SimpleCommentChecker implements CommentChecker {

	private String[] disallowUsernamePatterns;
	private String[] disallowEmailPatterns;

	@Autowired
	private UrlHelper urlHelper;
	@Autowired
	private UserQueryService userQueryService;

	@Override
	public void checkComment(Comment comment, CommentConfig config) throws LogicException {
		checkCommentUser(comment);
		checkCommentContent(comment, config);
	}

	protected void checkCommentUser(Comment comment) throws LogicException {
		if (Environment.isLogin()) {
			return;
		}
		String email = comment.getEmail();
		String name = comment.getNickname();
		String website = comment.getWebsite();
		User user = userQueryService.getUser();
		String emailOrAdmin = user.getEmail();
		if (!Validators.isEmptyOrNull(emailOrAdmin, true) && emailOrAdmin.equals(email)) {
			throw new LogicException("comment.email.invalid", "邮件不被允许");
		}
		if (user.getName().equalsIgnoreCase(name)) {
			throw new LogicException("comment.nickname.invalid", "昵称不被允许");
		}
		if (disallowUsernamePatterns != null && PatternMatchUtils.simpleMatch(disallowUsernamePatterns, name.trim())) {
			throw new LogicException("comment.username.invalid", "用户名不被允许");
		}

		if (email != null && disallowEmailPatterns != null
				&& PatternMatchUtils.simpleMatch(disallowEmailPatterns, email.trim())) {
			throw new LogicException("comment.email.invalid", "邮件不被允许");
		}
		if (website != null) {
			try {

				UriComponents uc = UriComponentsBuilder.fromHttpUrl(website).build();
				String host = uc.getHost();
				if (StringUtils.endsWithIgnoreCase(host, urlHelper.getUrlConfig().getRootDomain())) {
					throw new LogicException("comment.website.invalid", "网址不被允许");
				}
			} catch (Exception e) {
				throw new LogicException("comment.website.invalid", "网址不被允许");
			}
		}
	}

	protected void checkCommentContent(Comment comment, CommentConfig config) throws LogicException {
		//
	}

	public void setDisallowUsernamePatterns(String[] disallowUsernamePatterns) {
		this.disallowUsernamePatterns = disallowUsernamePatterns;
	}

	public void setDisallowEmailPatterns(String[] disallowEmailPatterns) {
		this.disallowEmailPatterns = disallowEmailPatterns;
	}
}
