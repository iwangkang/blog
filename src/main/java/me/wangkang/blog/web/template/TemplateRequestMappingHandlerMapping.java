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
package me.wangkang.blog.web.template;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo.BuilderConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import me.wangkang.blog.core.exception.SystemException;

/**
 * 一个提供注册TemplateRequestMapping的类
 * <p>
 * <b>由于默认的MappingRegistry是包访问权限，所以无法获取到它提供的读写锁,这样将无法在多个操作之间保持同步，所以这里额外的提供一把锁</b>
 * </p>
 * <p>
 * 这个类覆盖了所有AbstractHandlerMethodMapping中和MappingRegistry相关的方法
 * </p>
 * 
 * @see AbstractHandlerMethodMapping
 * @author Administrator
 *
 */
public final class TemplateRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

	private static final HandlerMethod PREFLIGHT_AMBIGUOUS_MATCH = new HandlerMethod(new EmptyHandler(),
			ClassUtils.getMethod(EmptyHandler.class, "handle"));

	private static final CorsConfiguration ALLOW_CORS_CONFIG = new CorsConfiguration();

	/**
	 * @since 5.6
	 */
	private RequestMappingInfo.BuilderConfiguration config;

	TemplateRequestMappingHandlerMapping() {
		super();
	}

	private final MappingRegistry mappingRegistry = new MappingRegistry();

	private List<TemplateInterceptor> templateInterceptors = new ArrayList<>();

	/**
	 * Return a (read-only) map with all mappings and HandlerMethod's.
	 */
	@Override
	public Map<RequestMappingInfo, HandlerMethod> getHandlerMethods() {
		StampedLock lock = mappingRegistry.getLock();
		long stamp = lock.tryOptimisticRead();
		Map<RequestMappingInfo, HandlerMethod> map = this.mappingRegistry.getMappings();
		if (!lock.validate(stamp)) {
			stamp = lock.readLock();
			try {
				map = this.mappingRegistry.getMappings();
			} finally {
				lock.unlockRead(stamp);
			}
		}
		return Collections.unmodifiableMap(map);
	}

	/**
	 * Register the given mapping.
	 * <p>
	 * This method may be invoked at runtime after initialization has completed.
	 * </p>
	 * 
	 * @param mapping
	 *            the mapping for the handler method
	 * @param handler
	 *            the handler
	 * @param method
	 *            the method
	 */
	@Override
	public void registerMapping(RequestMappingInfo mapping, Object handler, Method method) {
		StampedLock lock = mappingRegistry.getLock();
		long stamp = lock.writeLock();
		try {
			this.mappingRegistry.register(mapping, handler, method);
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * Un-register the given mapping.
	 * <p>
	 * This method may be invoked at runtime after initialization has completed.
	 * </p>
	 * 
	 * @see #lockWrite()
	 * @param mapping
	 *            the mapping to unregister
	 */
	@Override
	public void unregisterMapping(RequestMappingInfo mapping) {
		StampedLock lock = mappingRegistry.getLock();
		long stamp = lock.writeLock();
		try {
			this.mappingRegistry.unregister(mapping);
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * Register a handler method and its unique mapping. Invoked at startup for
	 * each detected handler method.
	 * 
	 * @param handler
	 *            the bean name of the handler or the handler instance
	 * @param method
	 *            the method to register
	 * @param mapping
	 *            the mapping conditions associated with the handler method
	 * @throws IllegalStateException
	 *             if another method was already registered under the same
	 *             mapping
	 */
	@Override
	protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
		StampedLock lock = mappingRegistry.getLock();
		long stamp = lock.writeLock();
		try {
			this.mappingRegistry.register(mapping, handler, method);
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	/**
	 * 获取MappingRegistry
	 * 
	 * @return
	 */
	public MappingRegistry getPublicMappingRegistry() {
		return mappingRegistry;
	}

	/**
	 * Return the handler methods for the given mapping name.
	 * 
	 * @param mappingName
	 *            the mapping name
	 * @return a list of matching HandlerMethod's or {@code null}; the returned
	 *         list will never be modified and is safe to iterate.
	 * @see #setHandlerMethodMappingNamingStrategy
	 */
	@Override
	public List<HandlerMethod> getHandlerMethodsForMappingName(String mappingName) {
		return this.mappingRegistry.getHandlerMethodsByMappingName(mappingName);
	}

	// Handler method lookup

	/**
	 * Look up a handler method for the given request.
	 */
	@Override
	protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
		StampedLock lock = mappingRegistry.getLock();
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		long stamp = lock.tryOptimisticRead();
		HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
		if (!lock.validate(stamp)) {
			stamp = lock.readLock();
			try {
				handlerMethod = lookupHandlerMethod(lookupPath, request);
			} finally {
				lock.unlockRead(stamp);
			}
		}
		return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
	}

	private void _addMatchingMappings(Collection<RequestMappingInfo> mappings, List<Match> matches,
			HttpServletRequest request) {
		for (RequestMappingInfo mapping : mappings) {
			RequestMappingInfo match = getMatchingMapping(mapping, request);
			if (match != null) {
				matches.add(new Match(match, this.mappingRegistry.getMappings().get(mapping)));
			}
		}
	}

	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		CorsConfiguration corsConfig = super.getCorsConfiguration(handler, request);
		if (handler instanceof HandlerMethod) {
			HandlerMethod handlerMethod = (HandlerMethod) handler;
			if (handlerMethod.equals(PREFLIGHT_AMBIGUOUS_MATCH)) {
				return ALLOW_CORS_CONFIG;
			} else {
				CorsConfiguration corsConfigFromMethod = this.mappingRegistry.getCorsConfiguration(handlerMethod);
				corsConfig = (corsConfig != null ? corsConfig.combine(corsConfigFromMethod) : corsConfigFromMethod);
			}
		}
		return corsConfig;
	}

	/**
	 * Look up the best-matching handler method for the current request. If
	 * multiple matches are found, the best match is selected.
	 * 
	 * @param lookupPath
	 *            mapping lookup path within the current servlet mapping
	 * @param request
	 *            the current request
	 * @return the best-matching handler method, or {@code null} if no match
	 * @see #handleMatch(Object, String, HttpServletRequest)
	 * @see #handleNoMatch(Set, String, HttpServletRequest)
	 */
	@Override
	protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
		List<Match> matches = new ArrayList<>();
		List<RequestMappingInfo> directPathMatches = this.mappingRegistry.getMappingsByUrl(lookupPath);
		if (directPathMatches != null) {
			addMatchingMappings(directPathMatches, matches, request);
		}
		if (matches.isEmpty()) {
			// No choice but to go through all mappings...
			_addMatchingMappings(this.mappingRegistry.getMappings().keySet(), matches, request);
		}

		if (!matches.isEmpty()) {
			Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
			Collections.sort(matches, comparator);
			Match bestMatch = matches.get(0);
			if (matches.size() > 1) {
				if (CorsUtils.isPreFlightRequest(request)) {
					return PREFLIGHT_AMBIGUOUS_MATCH;
				}
				// 这里找到了多个Match,
				// 因为允许注册{test}和{testasd}之类的路径，需要额外判断
				// 这里利用id来判断，id大的优先级高
				for (int i = 1; i < matches.size(); i++) {
					Match _bestMatch = matches.get(i);
					if (comparator.compare(bestMatch, _bestMatch) == 0) {
						Object bestMatchHandler = bestMatch.handlerMethod.getBean();
						Object _bestMatchHandler = _bestMatch.handlerMethod.getBean();
						if (bestMatchHandler instanceof TemplateController
								&& _bestMatchHandler instanceof TemplateController) {
							int id = ((TemplateController) bestMatchHandler).getId();
							int _id = ((TemplateController) _bestMatchHandler).getId();
							if (_id > id) {
								bestMatch = _bestMatch;
							}
						} else {
							Method m1 = bestMatch.handlerMethod.getMethod();
							Method m2 = _bestMatch.handlerMethod.getMethod();
							throw new IllegalStateException("Ambiguous handler methods mapped for HTTP path '"
									+ request.getRequestURL() + "': {" + m1 + ", " + m2 + "}");
						}
					}
				}
			}
			handleMatch(bestMatch.mapping, lookupPath, request);
			return bestMatch.handlerMethod;
		} else {
			return handleNoMatch(this.mappingRegistry.getMappings().keySet(), lookupPath, request);
		}
	}

	private void addMatchingMappings(Collection<RequestMappingInfo> mappings, List<Match> matches,
			HttpServletRequest request) {
		for (RequestMappingInfo mapping : mappings) {
			RequestMappingInfo match = getMatchingMapping(mapping, request);
			if (match != null) {
				matches.add(new Match(match, this.mappingRegistry.getMappings().get(mapping)));
			}
		}
	}

	/**
	 * @since 5.6
	 * @see RequestMappingInfo.Builder#options(BuilderConfiguration)
	 * @param builder
	 * @return
	 */
	public RequestMappingInfo createRequestMappingInfoWithConfig(RequestMappingInfo.Builder builder) {
		return builder.options(config).build();
	}

	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();

		templateInterceptors.addAll(BeanFactoryUtils
				.beansOfTypeIncludingAncestors(getApplicationContext(), TemplateInterceptor.class, true, false)
				.values());
		try {
			Field configField = this.getClass().getSuperclass().getDeclaredField("config");
			configField.setAccessible(true);
			this.config = (BuilderConfiguration) configField.get(this);
		} catch (Exception e) {
			throw new SystemException(e.getMessage(), e);
		}
	}

	@Override
	protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
		HandlerExecutionChain chain = super.getHandlerExecutionChain(handler, request);
		Optional<TemplateController> optional;
		if (!CollectionUtils.isEmpty(templateInterceptors)
				&& (optional = TemplateUtils.getTemplateController(handler)).isPresent()) {
			String templateName = ((TemplateController) optional.get()).getTemplateName();
			for (TemplateInterceptor interceptor : templateInterceptors) {
				if (interceptor.match(templateName,request)) {
					chain.addInterceptor(interceptor);
				}
			}
		}

		return chain;
	}

	private static class MappingRegistration {

		private final RequestMappingInfo mapping;

		private final HandlerMethod handlerMethod;

		private final List<String> directUrls;

		private final String mappingName;

		public MappingRegistration(RequestMappingInfo mapping, HandlerMethod handlerMethod, List<String> directUrls,
				String mappingName) {
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
			this.directUrls = (directUrls != null ? directUrls : Collections.<String>emptyList());
			this.mappingName = mappingName;
		}

		public RequestMappingInfo getMapping() {
			return this.mapping;
		}

		public HandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public List<String> getDirectUrls() {
			return this.directUrls;
		}

		public String getMappingName() {
			return this.mappingName;
		}
	}

	public class MappingRegistry {

		private final Map<RequestMappingInfo, MappingRegistration> registry = new HashMap<>();
		private final Map<RequestMappingInfo, HandlerMethod> mappingLookup = new LinkedHashMap<>();
		private final MultiValueMap<String, RequestMappingInfo> urlLookup = new LinkedMultiValueMap<>();
		private final Map<String, List<HandlerMethod>> nameLookup = new ConcurrentHashMap<>();
		private final Map<HandlerMethod, CorsConfiguration> corsLookup = new ConcurrentHashMap<>();

		private final StampedLock sl = new StampedLock();

		private MappingRegistry() {
			super();
		}

		/**
		 * 获取锁
		 * 
		 * @see StampedLock
		 * @return
		 */
		public StampedLock getLock() {
			return sl;
		}

		/**
		 * Return all mappings and handler methods
		 * <p>
		 * <b>NOT THREAD SAFE</b>
		 * </p>
		 * 
		 * @see #getLock()
		 * 
		 */
		public Map<RequestMappingInfo, HandlerMethod> getMappings() {
			return this.mappingLookup;
		}

		/**
		 * Return matches for the given URL path. Not thread-safe.
		 * 
		 * @see #acquireReadLock()
		 */
		public List<RequestMappingInfo> getMappingsByUrl(String urlPath) {
			return this.urlLookup.get(urlPath);
		}

		/**
		 * Return handler methods by mapping name. Thread-safe for concurrent
		 * use.
		 */
		public List<HandlerMethod> getHandlerMethodsByMappingName(String mappingName) {
			return this.nameLookup.get(mappingName);
		}

		/**
		 * Return CORS configuration. Thread-safe for concurrent use.
		 */
		public CorsConfiguration getCorsConfiguration(HandlerMethod handlerMethod) {
			HandlerMethod original = handlerMethod.getResolvedFromHandlerMethod();
			return this.corsLookup.get(original != null ? original : handlerMethod);
		}

		/**
		 * 注册一个RequestMapping
		 * <p>
		 * <b>NOT THREAD SAFE</b>
		 * </p>
		 * 
		 * @see #getLock()
		 * @param mapping
		 * @param handler
		 * @param method
		 */
		public void register(RequestMappingInfo mapping, Object handler, Method method) {
			HandlerMethod handlerMethod = createHandlerMethod(handler, method);
			assertUniqueMethodMapping(handlerMethod, mapping);
			this.mappingLookup.put(mapping, handlerMethod);

			List<String> directUrls = getDirectUrls(mapping);
			for (String url : directUrls) {
				this.urlLookup.add(url, mapping);
			}

			String name = null;
			if (getNamingStrategy() != null) {
				name = getNamingStrategy().getName(handlerMethod, mapping);
				addMappingName(name, handlerMethod);
			}

			CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
			if (corsConfig != null) {
				this.corsLookup.put(handlerMethod, corsConfig);
			}

			this.registry.put(mapping, new MappingRegistration(mapping, handlerMethod, directUrls, name));
		}

		private void assertUniqueMethodMapping(HandlerMethod newHandlerMethod, RequestMappingInfo mapping) {
			HandlerMethod handlerMethod = this.mappingLookup.get(mapping);
			if (handlerMethod != null && !handlerMethod.equals(newHandlerMethod)) {
				throw new IllegalStateException("Ambiguous mapping. Cannot map '" + newHandlerMethod.getBean()
						+ "' method \n" + newHandlerMethod + "\nto " + mapping + ": RequestMappingInfohere is already '"
						+ handlerMethod.getBean() + "' bean method\n" + handlerMethod + " mapped.");
			}
		}

		private List<String> getDirectUrls(RequestMappingInfo mapping) {
			List<String> urls = new ArrayList<>(1);
			for (String path : getMappingPathPatterns(mapping)) {
				if (!getPathMatcher().isPattern(path)) {
					urls.add(path);
				}
			}
			return urls;
		}

		private void addMappingName(String name, HandlerMethod handlerMethod) {
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				oldList = Collections.<HandlerMethod>emptyList();
			}

			for (HandlerMethod current : oldList) {
				if (handlerMethod.equals(current)) {
					return;
				}
			}

			List<HandlerMethod> newList = new ArrayList<>(oldList.size() + 1);
			newList.addAll(oldList);
			newList.add(handlerMethod);
			this.nameLookup.put(name, newList);
		}

		/**
		 * 解除注册一个RequestMapping
		 * <p>
		 * <b>NOT THREAD SAFE</b>
		 * </p>
		 * 
		 * @see #getLock()
		 * @param mapping
		 */
		public void unregister(RequestMappingInfo mapping) {
			MappingRegistration definition = this.registry.remove(mapping);
			if (definition == null) {
				return;
			}
			this.mappingLookup.remove(definition.getMapping());

			for (String url : definition.getDirectUrls()) {
				List<RequestMappingInfo> list = this.urlLookup.get(url);
				if (list != null) {
					list.remove(definition.getMapping());
					if (list.isEmpty()) {
						this.urlLookup.remove(url);
					}
				}
			}

			removeMappingName(definition);

			this.corsLookup.remove(definition.getHandlerMethod());
		}

		private void removeMappingName(MappingRegistration definition) {
			String name = definition.getMappingName();
			if (name == null) {
				return;
			}
			HandlerMethod handlerMethod = definition.getHandlerMethod();
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				return;
			}
			if (oldList.size() <= 1) {
				this.nameLookup.remove(name);
				return;
			}
			List<HandlerMethod> newList = new ArrayList<>(oldList.size() - 1);
			for (HandlerMethod current : oldList) {
				if (!current.equals(handlerMethod)) {
					newList.add(current);
				}
			}
			this.nameLookup.put(name, newList);
		}
	}

	/**
	 * A thin wrapper around a matched HandlerMethod and its mapping, for the
	 * purpose of comparing the best match with a comparator in the context of
	 * the current request.
	 */
	private class Match {

		private final RequestMappingInfo mapping;

		private final HandlerMethod handlerMethod;

		public Match(RequestMappingInfo mapping, HandlerMethod handlerMethod) {
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
		}

		@Override
		public String toString() {
			return this.mapping.toString();
		}
	}

	private class MatchComparator implements Comparator<Match> {

		private final Comparator<RequestMappingInfo> comparator;

		public MatchComparator(Comparator<RequestMappingInfo> comparator) {
			this.comparator = comparator;
		}

		@Override
		public int compare(Match match1, Match match2) {
			return this.comparator.compare(match1.mapping, match2.mapping);
		}
	}

	private static class EmptyHandler {

		@SuppressWarnings("unused")
		public void handle() {
			throw new UnsupportedOperationException("not implemented");
		}
	}

}
