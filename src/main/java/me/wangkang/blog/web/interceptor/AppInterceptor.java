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
package me.wangkang.blog.web.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.entity.User;
import me.wangkang.blog.core.exception.SpaceNotFoundException;
import me.wangkang.blog.core.lock.LockException;
import me.wangkang.blog.core.lock.LockHelper;
import me.wangkang.blog.core.lock.LockKey;
import me.wangkang.blog.core.lock.LockKeyContext;
import me.wangkang.blog.core.lock.LockManager;
import me.wangkang.blog.core.security.AuthencationException;
import me.wangkang.blog.core.security.EnsureLogin;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.SpaceService;
import me.wangkang.blog.util.UrlUtils;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.IPGetter;
import me.wangkang.blog.web.RequestMatcher;
import me.wangkang.blog.web.Webs;
import me.wangkang.blog.web.security.CsrfException;
import me.wangkang.blog.web.security.CsrfToken;
import me.wangkang.blog.web.security.CsrfTokenRepository;
import me.wangkang.blog.web.security.InvalidCsrfTokenException;
import me.wangkang.blog.web.security.MissingCsrfTokenException;

public class AppInterceptor extends HandlerInterceptorAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(AppInterceptor.class);

	@Autowired
	private SpaceService spaceService;
	@Autowired
	private LockManager lockManager;

	@Autowired
	private CsrfTokenRepository tokenRepository;

	private RequestMatcher requireCsrfProtectionMatcher = new DefaultRequiresCsrfMatcher();

	private IPGetter ipGetter = new IPGetter();

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (isHandlerMethod(handler)) {
			String spaceAlias = Webs.getSpaceFromRequest(request);
			try {
				HttpSession session = request.getSession(false);
				User user = null;
				if (session != null) {
					user = (User) session.getAttribute(Constants.USER_SESSION_KEY);
				}

				Environment.setUser(user);
				enableLogin((HandlerMethod) handler);

				setLockKeys(request);

				if (spaceAlias != null) {
					Space space = spaceService.getSpace(spaceAlias)
							.orElseThrow(() -> new SpaceNotFoundException(spaceAlias));

					if (space.getIsPrivate()) {
						Environment.doAuthencation();
					}

					if (space.hasLock() && !Webs.unlockRequest(request)) {
						lockManager.openLock(space);
					}

					Environment.setSpace(space);
				}

				Environment.setIP(ipGetter.getIp(request));

				csrfCheck(request, response);

			} catch (AuthencationException | SpaceNotFoundException | LockException | CsrfException e) {
				removeContext();
				throw e;
			} catch (Throwable e) {
				removeContext();
				// 防止死循环
				LOGGER.error(e.getMessage(), e);
				return false;
			}
		}
		return true;
	}

	private void enableLogin(HandlerMethod methodHandler) {
		// auth check
		getAnnotation(methodHandler.getMethod(), EnsureLogin.class).ifPresent(ann -> Environment.doAuthencation());
	}

	/**
	 * 将session中的解锁钥匙放入上下文中
	 * 
	 * @param request
	 */
	private void setLockKeys(HttpServletRequest request) {
		Map<String, List<LockKey>> keysMap = LockHelper.getKeysMap(request);
		if (!CollectionUtils.isEmpty(keysMap)) {
			LOGGER.debug("将LockKey放入LockKeyContext中:{}", keysMap);
			LockKeyContext.set(keysMap);
		}
	}

	private <T extends Annotation> Optional<T> getAnnotation(Method method, Class<T> annotationType) {
		T t = AnnotationUtils.findAnnotation(method, annotationType);
		if (t == null) {
			t = AnnotationUtils.findAnnotation(method.getDeclaringClass(), annotationType);
		}
		return Optional.ofNullable(t);
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
			throws Exception {
		removeContext();
	}

	@Override
	public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		removeContext();
	}

	private void removeContext() {
		Environment.remove();
		LockKeyContext.remove();
	}

	private void csrfCheck(HttpServletRequest request, HttpServletResponse response) {
		CsrfToken csrfToken = tokenRepository.loadToken(request);
		final boolean missingToken = csrfToken == null;
		if (missingToken) {
			CsrfToken generatedToken = tokenRepository.generateToken(request);
			csrfToken = new SaveOnAccessCsrfToken(tokenRepository, request, response, generatedToken);
		}
		request.setAttribute(CsrfToken.class.getName(), csrfToken);
		request.setAttribute(csrfToken.getParameterName(), csrfToken);
		if ("get".equalsIgnoreCase(request.getMethod())) {
			// GET请求不能检查，否则死循环
			return;
		}

		if (!requireCsrfProtectionMatcher.match(request)) {
			return;
		}
		String actualToken = request.getHeader(csrfToken.getHeaderName());
		if (actualToken == null) {
			actualToken = request.getParameter(csrfToken.getParameterName());
		}
		if (!csrfToken.getToken().equals(actualToken)) {
			LOGGER.debug("Invalid CSRF token found for {}", UrlUtils.buildFullRequestUrl(request));
			if (missingToken) {
				throw new MissingCsrfTokenException(actualToken);
			} else {
				throw new InvalidCsrfTokenException(csrfToken, actualToken);
			}
		}
	}

	@SuppressWarnings("serial")
	private static final class SaveOnAccessCsrfToken implements CsrfToken {
		private transient CsrfTokenRepository tokenRepository;
		private transient HttpServletRequest request;
		private transient HttpServletResponse response;

		private final CsrfToken delegate;

		public SaveOnAccessCsrfToken(CsrfTokenRepository tokenRepository, HttpServletRequest request,
				HttpServletResponse response, CsrfToken delegate) {
			super();
			this.tokenRepository = tokenRepository;
			this.request = request;
			this.response = response;
			this.delegate = delegate;
		}

		@Override
		public String getHeaderName() {
			return delegate.getHeaderName();
		}

		@Override
		public String getParameterName() {
			return delegate.getParameterName();
		}

		@Override
		public String getToken() {
			saveTokenIfNecessary();
			return delegate.getToken();
		}

		@Override
		public String toString() {
			return "SaveOnAccessCsrfToken [delegate=" + delegate + "]";
		}

		@Override
		public int hashCode() {
			return Objects.hash(delegate);
		}

		@Override
		public boolean equals(Object obj) {
			if (Validators.baseEquals(this, obj)) {
				SaveOnAccessCsrfToken other = (SaveOnAccessCsrfToken) obj;
				return Objects.equals(this.delegate, other.delegate);
			}
			return false;
		}

		private void saveTokenIfNecessary() {
			if (this.tokenRepository == null) {
				return;
			}

			synchronized (this) {
				if (tokenRepository != null) {
					this.tokenRepository.saveToken(delegate, request, response);
					this.tokenRepository = null;
					this.request = null;
					this.response = null;
				}
			}
		}

	}

	private static final class DefaultRequiresCsrfMatcher implements RequestMatcher {
		private Pattern allowedMethods = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.springframework.security.web.util.matcher.RequestMatcher#matches(
		 * javax.servlet.http.HttpServletRequest)
		 */
		public boolean match(HttpServletRequest request) {
			return !allowedMethods.matcher(request.getMethod()).matches();
		}
	}

	private boolean isHandlerMethod(Object handler) {
		return handler instanceof HandlerMethod;
	}

	public void setRequireCsrfProtectionMatcher(RequestMatcher requireCsrfProtectionMatcher) {
		this.requireCsrfProtectionMatcher = requireCsrfProtectionMatcher;
	}

	public void setIpGetter(IPGetter ipGetter) {
		Objects.requireNonNull(ipGetter);
		this.ipGetter = ipGetter;
	}
}
