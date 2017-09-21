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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.entity.User;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.security.AttemptLogger;
import me.wangkang.blog.core.security.AttemptLoggerManager;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.security.GoogleAuthenticator;
import me.wangkang.blog.core.service.UserService;
import me.wangkang.blog.web.CaptchaValidator;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.security.CsrfToken;
import me.wangkang.blog.web.security.CsrfTokenRepository;
import me.wangkang.blog.web.template.TemplateRequestMappingHandlerMapping;
import me.wangkang.blog.web.validator.LoginBean;
import me.wangkang.blog.web.validator.LoginBeanValidator;

@Controller("loginController")
public class LoginController implements InitializingBean {

	@Autowired
	private CsrfTokenRepository csrfTokenRepository;
	@Autowired
	private LoginBeanValidator loginBeanValidator;
	@Autowired
	private UserService userService;

	@Autowired(required = false)
	private GoogleAuthenticator ga;

	@Autowired
	private CaptchaValidator captchaValidator;

	@Autowired
	private TemplateRequestMappingHandlerMapping mapping;

	/**
	 * 当用户用户名和密码校验通过，但是还没有通过GoogleAuthenticator校验是，先将用户放在这个key中
	 */
	private static final String GA_SESSION_KEY = "ga_user";

	private final Message otpVerifyFail = new Message("otp.verifyFail", "动态口令校验失败");
	private final Message pwdVerifyRequire = new Message("pwd.verifyRequire", "请先通过密码验证");

	// 是否支持改变sessionid,需要运行容器支持servlet3.1+
	private static boolean SUPPORT_CHANGE_SESSION_ID;

	static {
		try {
			HttpServletRequest.class.getMethod("changeSessionId");
			SUPPORT_CHANGE_SESSION_ID = true;
		} catch (Exception e) {
			SUPPORT_CHANGE_SESSION_ID = false;
		}
	}

	@Value("${login.attempt.count:5}")
	private int attemptCount;

	@Value("${login.attempt.maxCount:100}")
	private int maxAttemptCount;

	@Value("${login.attempt.sleepSec:300}")
	private int sleepSec;

	@Autowired
	private AttemptLoggerManager attemptLoggerManager;
	private AttemptLogger attemptLogger;

	@InitBinder(value = "loginBean")
	protected void initBinder(WebDataBinder binder) {
		binder.setValidator(loginBeanValidator);
	}

	@PostMapping(value = "login")
	@ResponseBody
	public JsonResult login(@RequestBody @Validated LoginBean loginBean, HttpServletRequest request,
			HttpServletResponse response) throws LogicException {
		String ip = Environment.getIP();
		if (attemptLogger.log(ip)) {
			captchaValidator.doValidate(request);
		}
		User user = userService.login(loginBean);
		if (ga != null) {
			request.getSession().setAttribute(GA_SESSION_KEY, user);
			return new JsonResult(false, new Message("otp.required", "请输入动态口令"));
		}
		String lastAuthencationFailUrl = successLogin(user, request, response);
		return new JsonResult(true, lastAuthencationFailUrl);
	}

	@GetMapping("login/needCaptcha")
	@ResponseBody
	public boolean needCaptcha() {
		return attemptLogger.reach(Environment.getIP());
	}

	@ResponseBody
	public JsonResult otpVerify(@RequestParam("code") String codeStr, HttpServletRequest request,
			HttpServletResponse response) throws LogicException {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return new JsonResult(false, pwdVerifyRequire);
		}
		// 没有通过用户名密码认证，无需校验
		User user = (User) session.getAttribute(GA_SESSION_KEY);
		if (user == null) {
			return new JsonResult(false, pwdVerifyRequire);
		}

		String ip = Environment.getIP();
		if (attemptLogger.log(ip)) {
			captchaValidator.doValidate(request);
		}

		if (!ga.checkCode(codeStr)) {
			return new JsonResult(false, otpVerifyFail);
		}
		session.removeAttribute(GA_SESSION_KEY);
		String lastAuthencationFailUrl = successLogin(user, request, response);
		return new JsonResult(true, lastAuthencationFailUrl);
	}

	private String successLogin(User user, HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession();
		session.setAttribute(Constants.USER_SESSION_KEY, user);
		changeSessionId(request);
		changeCsrf(request, response);

		attemptLogger.remove(Environment.getIP());

		String lastAuthencationFailUrl = (String) session.getAttribute(Constants.LAST_AUTHENCATION_FAIL_URL);
		if (lastAuthencationFailUrl != null) {
			session.removeAttribute(Constants.LAST_AUTHENCATION_FAIL_URL);
		}
		return lastAuthencationFailUrl;
	}

	private void changeSessionId(HttpServletRequest request) {
		if (SUPPORT_CHANGE_SESSION_ID) {
			request.changeSessionId();
		}
	}

	private void changeCsrf(HttpServletRequest request, HttpServletResponse response) {
		// 更改 csrf
		boolean containsToken = csrfTokenRepository.loadToken(request) != null;
		if (containsToken) {
			this.csrfTokenRepository.saveToken(null, request, response);

			CsrfToken newToken = this.csrfTokenRepository.generateToken(request);
			this.csrfTokenRepository.saveToken(newToken, request, response);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.attemptLogger = attemptLoggerManager.createAttemptLogger(attemptCount, maxAttemptCount, sleepSec);

		if (ga != null) {

			mapping.registerMapping(
					mapping.createRequestMappingInfoWithConfig(
							RequestMappingInfo.paths("login/otpVerify").methods(RequestMethod.POST)),
					"loginController", LoginController.class.getMethod("otpVerify", String.class,
							HttpServletRequest.class, HttpServletResponse.class));
		}
	}
}
