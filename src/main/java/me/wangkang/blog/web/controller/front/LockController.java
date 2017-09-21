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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.lock.Lock;
import me.wangkang.blog.core.lock.LockBean;
import me.wangkang.blog.core.lock.LockHelper;
import me.wangkang.blog.core.lock.LockKey;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.web.CaptchaValidator;
import me.wangkang.blog.web.JsonResult;

@Controller
public class LockController {

	@Autowired
	private UrlHelper urlHelper;
	@Autowired
	private CaptchaValidator captchaValidator;

	@PostMapping({ "space/{alias}/unlock", "/unlock" })
	public String unlock(HttpServletRequest request, RedirectAttributes ra) {
		LockBean lockBean = LockHelper.getLockBean(request);
		if (lockBean == null) {
			return "redirect:" + urlHelper.getUrl();
		}
		Lock lock = lockBean.getLock();
		LockKey key = null;
		try {
			captchaValidator.doValidate(request);
			key = lock.getKeyFromRequest(request);
		} catch (LogicException e) {
			ra.addFlashAttribute(Constants.ERROR, e.getLogicMessage());
			return buildLockUrl(lockBean.getSpaceAlias());
		}
		LockHelper.addKey(request, key, lockBean.getLockResource());
		LockHelper.clearLockBean(request);
		return "redirect:" + lockBean.getRedirectUrl();
	}

	private String buildLockUrl(String alias) {
		if (alias == null) {
			return "redirect:" + urlHelper.getUrl() + "/unlock";
		} else {
			return "redirect:" + urlHelper.getUrls().getUrl(new Space(alias)) + "/unlock";
		}
	}

	@PostMapping(value = { "space/{alias}/unlock", "/unlock" }, headers = "x-requested-with=XMLHttpRequest")
	@ResponseBody
	public JsonResult unlock(HttpServletRequest request) throws LogicException {
		LockBean lockBean = LockHelper.getLockBean(request);
		if (lockBean == null) {
			return new JsonResult(false, new Message("lock.miss", "锁缺失"));
		}
		captchaValidator.doValidate(request);
		Lock lock = lockBean.getLock();
		LockKey key = lock.getKeyFromRequest(request);
		LockHelper.addKey(request, key, lockBean.getLockResource());
		lock.tryOpen(key);
		LockHelper.clearLockBean(request);
		return new JsonResult(true, lockBean.getRedirectUrl());
	}
}
