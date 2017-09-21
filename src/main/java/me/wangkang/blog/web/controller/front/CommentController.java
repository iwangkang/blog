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
package me.wangkang.blog.web.controller.front;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.entity.Comment;
import me.wangkang.blog.core.entity.CommentModule;
import me.wangkang.blog.core.entity.CommentModule.ModuleType;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.security.AttemptLogger;
import me.wangkang.blog.core.security.AttemptLoggerManager;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.impl.CommentService;
import me.wangkang.blog.web.CaptchaValidator;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.validator.CommentValidator;

@Controller("commentController")
public class CommentController implements InitializingBean {

	@Autowired
	private CommentService commentService;
	@Autowired
	private CommentValidator commentValidator;
	@Autowired
	private UrlHelper urlHelper;
	@Autowired
	private CaptchaValidator captchaValidator;
	@Autowired
	private AttemptLoggerManager attemptLoggerManager;

	@Value("${comment.attempt.count:5}")
	private int attemptCount;

	@Value("${comment.attempt.maxCount:50}")
	private int maxAttemptCount;

	@Value("${comment.attempt.sleepSec:60}")
	private int sleepSec;

	private AttemptLogger attemptLogger;

	@InitBinder(value = "comment")
	protected void initCommentBinder(WebDataBinder binder) {
		binder.setValidator(commentValidator);
	}

	@GetMapping("comment/config")
	@ResponseBody
	public JsonResult getConfig() {
		return new JsonResult(true, commentService.getCommentConfig());
	}

	@PostMapping({ "space/{alias}/{type}/{id}/addComment", "{type}/{id}/addComment" })
	@ResponseBody
	public JsonResult addComment(@RequestBody @Validated Comment comment, @PathVariable("type") String type,
			@PathVariable("id") Integer moduleId, HttpServletRequest req) throws LogicException {
		if (!Environment.isLogin() && attemptLogger.log(Environment.getIP())) {
			captchaValidator.doValidate(req);
		}
		comment.setCommentModule(new CommentModule(getModuleType(type), moduleId));
		comment.setIp(Environment.getIP());
		return new JsonResult(true, commentService.insertComment(comment));
	}

	@GetMapping({ "space/{alias}/{type}/{id}/comment/{commentId}/conversations",
			"{type}/{id}/comment/{commentId}/conversations" })
	@ResponseBody
	public JsonResult queryConversations(@PathVariable("type") String type, @PathVariable("id") Integer moduleId,
			@PathVariable("commentId") Integer commentId) throws LogicException {
		return new JsonResult(true,
				commentService.queryConversations(new CommentModule(getModuleType(type), moduleId), commentId));
	}

	@GetMapping("comment/link/{type}/{id}")
	public String redic(@PathVariable("type") String type, @PathVariable("id") Integer moduleId) throws LogicException {
		return "redirect:"
				+ commentService.getLink(new CommentModule(getModuleType(type), moduleId)).orElse(urlHelper.getUrl());
	}

	@GetMapping("comment/needCaptcha")
	@ResponseBody
	public boolean needCaptcha() {
		return !Environment.isLogin() && attemptLogger.reach(Environment.getIP());
	}

	private ModuleType getModuleType(String type) {
		try {
			return ModuleType.valueOf(type.toUpperCase());
		} catch (Exception e) {
			throw new TypeMismatchException(type, ModuleType.class);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.attemptLogger = attemptLoggerManager.createAttemptLogger(attemptCount, maxAttemptCount, sleepSec);
	}
}
