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
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.security.CsrfTokenRepository;

@Controller
public class LogoutController {
	@Autowired
	private CsrfTokenRepository csrfTokenRepository;

	@PostMapping("logout")
	public String logout(HttpServletRequest request, HttpServletResponse response) {
		clearAuthencation(request, response);
		return "redirect:/";
	}

	@PostMapping(value = "logout", headers = "x-requested-with=XMLHttpRequest")
	@ResponseBody
	public JsonResult ajaxLogout(HttpServletRequest request, HttpServletResponse response) {
		clearAuthencation(request, response);
		return new JsonResult(true, new Message("logout.success", "注销成功"));
	}

	private void clearAuthencation(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
			Environment.setUser(null);
		}
		csrfTokenRepository.saveToken(null, request, response);
	}
}
