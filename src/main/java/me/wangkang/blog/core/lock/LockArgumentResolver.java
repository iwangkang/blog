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
package me.wangkang.blog.core.lock;

import java.io.InputStream;
import java.lang.reflect.Type;

import javax.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.lock.SysLock.SysLockType;
import me.wangkang.blog.util.Jsons;
import me.wangkang.blog.util.Resources;
import me.wangkang.blog.util.Validators;

/**
 * 锁属性赋值器
 * 
 * @author Administrator
 *
 */
public class LockArgumentResolver implements HandlerMethodArgumentResolver {

	private SysLockValidator validator = new SysLockValidator();

	private static final String LOCK_NAME = "lock";

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(RequestLock.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
		final HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		SysLock lock = null;
		try {
			lock = getLockFromRequest(servletRequest);
		} catch (Exception e) {
			throw new HttpMessageNotReadableException(e.getMessage(), e);
		}
		// 做验证
		WebDataBinder binder = binderFactory.createBinder(webRequest, lock, LOCK_NAME);
		binder.setValidator(validator);
		binder.validate();
		BindingResult bindingResult = binder.getBindingResult();
		if (bindingResult.hasErrors()) {
			throw new MethodArgumentNotValidException(parameter, bindingResult);
		}
		mavContainer.addAttribute(BindingResult.MODEL_KEY_PREFIX + LOCK_NAME, binder.getBindingResult());
		return lock;
	}

	private SysLock getLockFromRequest(HttpServletRequest request) throws Exception {
		InputStream is = request.getInputStream();
		return Jsons.readValue(SysLock.class, Resources.read(is));
	}

	public static final class SysLockDeserializer implements JsonDeserializer<SysLock> {

		@Override
		public SysLock deserialize(JsonElement element, Type t, JsonDeserializationContext context)
				throws JsonParseException {
			if (element.isJsonObject()) {
				JsonObject obj = element.getAsJsonObject();
				SysLockType type = SysLockType.valueOf(obj.get("type").getAsString());
				switch (type) {
				case QA:
					return Jsons.readValue(QALock.class, element);
				case PASSWORD:
					return Jsons.readValue(PasswordLock.class, element);
				default:
					throw new HttpMessageNotReadableException("未知的锁对象:" + type);
				}
			}
			throw new HttpMessageNotReadableException("无法将" + element + "转换为锁对象");
		}

	}

	private final class SysLockValidator implements Validator {

		private static final int MAX_NAME_LENGTH = 20;
		private static final int MAX_PASSWORD_LENGTH = 16;
		private static final int MAX_QUESTION_LENGTH = 10000;
		private static final int MAX_ANSWERS_LENGTH = 500;
		private static final int MAX_ANSWERS_SIZE = 10;// 答案的个数

		@Override
		public boolean supports(Class<?> clazz) {
			return SysLock.class.isAssignableFrom(clazz);
		}

		@Override
		public void validate(Object target, Errors errors) {
			SysLock lock = (SysLock) target;
			String name = lock.getName();
			if (Validators.isEmptyOrNull(name, true)) {
				errors.reject("lock.name.empty", "锁的名称不能为空");
				return;
			}
			if (name.length() > MAX_NAME_LENGTH) {
				errors.reject("lock.name.toolong", "锁的名称不能超过" + MAX_NAME_LENGTH + "个字符");
				return;
			}
			switch (lock.getType()) {
			case PASSWORD:
				PasswordLock plock = (PasswordLock) lock;
				validPasswordLock(plock, errors);
				break;
			case QA:
				QALock qaLock = (QALock) lock;
				validQALock(qaLock, errors);
				break;
			default:
				throw new SystemException("无法处理的锁类型：" + lock.getLockType());
			}
		}

		private void validPasswordLock(PasswordLock plock, Errors errors) {
			String password = plock.getPassword();
			if (Validators.isEmptyOrNull(password, true)) {
				errors.reject("lock.pwd.empty", "锁的密码不能为空");
				return;
			}
			if (password.length() > MAX_PASSWORD_LENGTH) {
				errors.reject("lock.pwd.toolong", "锁的密码不能超过" + MAX_PASSWORD_LENGTH + "个字符");
				return;
			}
		}

		private void validQALock(QALock qaLock, Errors errors) {
			String question = qaLock.getQuestion();
			if (Validators.isEmptyOrNull(question, true)) {
				errors.reject("lock.question.empty", "问题不能为空");
				return;
			}
			if (question.length() > MAX_QUESTION_LENGTH) {
				errors.reject("lock.question.toolong", "问题不能超过" + MAX_QUESTION_LENGTH + "个字符");
				return;
			}

			String answers = qaLock.getAnswers();
			if (answers == null || answers.isEmpty()) {
				errors.reject("lock.answers.empty", "答案不能为空");
				return;
			}
			if (answers.length() > MAX_ANSWERS_LENGTH) {
				errors.reject("lock.answers.toolong", "答案不能超过" + MAX_ANSWERS_LENGTH + "个字符");
				return;
			}
			String[] answerArray = answers.split(",");
			if (answerArray.length > MAX_ANSWERS_SIZE) {
				errors.reject("lock.answers.oversize", "答案不能超过" + MAX_ANSWERS_SIZE + "个");
				return;
			}
		}
	}

}
