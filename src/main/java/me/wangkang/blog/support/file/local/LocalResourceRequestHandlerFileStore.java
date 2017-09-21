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
package me.wangkang.blog.support.file.local;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.file.CommonFile;
import me.wangkang.blog.core.file.FileStore;
import me.wangkang.blog.core.file.FileStoreRegisterEvent;
import me.wangkang.blog.core.file.ThumbnailUrl;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.UrlUtils;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.RequestMatcher;
import me.wangkang.blog.web.Webs;
import me.wangkang.blog.web.template.TemplateRequestMappingHandlerMapping;

/**
 * 将资源存储和资源访问结合起来，<b>这个类必须在Web环境中注册</b>
 * 
 * 
 * @see ResourceHttpRequestHandler
 * @author mhlx
 *
 */
class LocalResourceRequestHandlerFileStore extends ResourceHttpRequestHandler
		implements FileStore, ApplicationEventPublisherAware {

	private static final Logger LOG = LoggerFactory.getLogger(LocalResourceRequestHandlerFileStore.class);

	protected int id;
	private String name;
	protected String absPath;
	protected String urlPrefix;
	protected Path absFolder;
	private RequestMatcher requestMatcher;// 防盗链处理
	private final String urlPatternPrefix;
	private boolean readOnly;

	@Autowired
	protected UrlHelper urlHelper;

	private ApplicationEventPublisher applicationEventPublisher;

	@Autowired
	private TemplateRequestMappingHandlerMapping requestMappingHandlerMapping;

	private static Method method;

	static {
		try {
			method = HttpRequestHandler.class.getMethod("handleRequest", HttpServletRequest.class,
					HttpServletResponse.class);
		} catch (Exception e) {
			throw new SystemException(e.getMessage(), e);
		}
	}

	public LocalResourceRequestHandlerFileStore(String urlPatternPrefix) {
		super();
		if (!Validators.isLetterOrNum(urlPatternPrefix)) {
			throw new SystemException("处理器路径不能为空，且只能由字母或数字组成");
		}
		if (!urlPatternPrefix.startsWith("/")) {
			urlPatternPrefix = "/" + urlPatternPrefix;
		}
		this.urlPatternPrefix = urlPatternPrefix;
	}

	@Override
	public int id() {
		return id;
	}

	@Override
	public CommonFile store(String key, MultipartFile mf) throws LogicException {
		Path dest = FileUtils.sub(absFolder, key);
		if (FileUtils.exists(dest) && !FileUtils.deleteQuietly(dest)) {
			String absPath = dest.toAbsolutePath().toString();
			throw new LogicException("file.store.exists", "文件" + absPath + "已经存在", absPath);
		}
		String originalFilename = mf.getOriginalFilename();
		try {
			FileUtils.forceMkdir(dest.getParent());
			Webs.save(mf, dest);
		} catch (IOException e) {
			throw new SystemException(e.getMessage(), e);
		}
		CommonFile cf = new CommonFile();
		cf.setExtension(FileUtils.getFileExtension(originalFilename));
		cf.setSize(mf.getSize());
		cf.setStore(id);
		cf.setOriginalFilename(originalFilename);

		return cf;
	}

	@Override
	public boolean delete(String key) {
		Path p = FileUtils.sub(absFolder, key);
		if (FileUtils.exists(p)) {
			return FileUtils.deleteQuietly(p);
		}
		return true;
	}

	@Override
	public boolean deleteBatch(String key) {
		return delete(key);
	}

	@Override
	public String getUrl(String key) {
		String path = key;
		if (!key.startsWith("/")) {
			path = "/" + key;
		}
		return urlPrefix + Validators.cleanPath(path);
	}

	@Override
	public Optional<ThumbnailUrl> getThumbnailUrl(String key) {
		return Optional.empty();
	}

	@Override
	protected final Resource getResource(HttpServletRequest request) throws IOException {
		if (requestMatcher != null && !requestMatcher.match(request)) {
			return null;
		}
		return this.findResource(request);
	}

	protected final String getPath(HttpServletRequest request) {
		String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
		if (path == null) {
			throw new IllegalStateException("Required request attribute '"
					+ HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE + "' is not set");
		}
		path = processPath(path);
		if (!StringUtils.hasText(path) || isInvalidPath(path)) {
			return null;
		}
		if (path.contains("%")) {
			try {
				if (isInvalidPath(URLDecoder.decode(path, "UTF-8"))) {
					return null;
				}
			} catch (IllegalArgumentException ex) {
				// ignore
			} catch (UnsupportedEncodingException e) {
				throw new SystemException(e.getMessage(), e);
			}
		}
		if (path.startsWith(urlPatternPrefix)) {
			path = path.substring(urlPatternPrefix.length());
		}
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		return path;
	}

	protected Resource findResource(HttpServletRequest request) throws IOException {
		Path file = absFolder.resolve(getPath(request));
		return FileUtils.exists(file) ? new PathResource(file) : null;
	}

	@Override
	public boolean readOnly() {
		return readOnly;
	}

	@Override
	public boolean copy(String oldPath, String path) {
		Optional<Path> optionalOld = getFile(oldPath);
		if (optionalOld.isPresent()) {
			try {
				FileUtils.copy(optionalOld.get(), FileUtils.sub(absFolder, path));
				return true;
			} catch (IOException e) {
				LOG.error("拷贝文件失败:" + e.getMessage(), e);
				return false;
			}
		}
		return false;
	}

	@Override
	public boolean move(String oldPath, String path) {
		Optional<Path> optionalOld = getFile(oldPath);
		if (optionalOld.isPresent()) {
			try {
				FileUtils.move(optionalOld.get(), FileUtils.sub(absFolder, path));
				return true;
			} catch (IOException e) {
				LOG.error("移动文件失败:" + e.getMessage(), e);
				return false;
			}
		}
		return false;
	}

	protected Optional<Path> getFile(String oldPath) {
		Path p = FileUtils.sub(absFolder, oldPath);
		if (FileUtils.exists(p) && FileUtils.isRegularFile(p)) {
			return Optional.of(p);
		}
		return Optional.empty();
	}

	@Override
	public final void afterPropertiesSet() throws Exception {

		if (absPath == null) {
			throw new SystemException("文件存储路径不能为null");
		}

		absFolder = Paths.get(absPath);
		FileUtils.forceMkdir(absFolder);

		// 忽略location的警告
		moreAfterPropertiesSet();

		if (urlPrefix == null || !UrlUtils.isAbsoluteUrl(urlPrefix)) {
			urlPrefix = urlHelper.getUrl() + urlPatternPrefix;
		}

		/**
		 * spring 4.3 fix
		 */
		super.afterPropertiesSet();

		this.applicationEventPublisher.publishEvent(new FileStoreRegisterEvent(this, this));
		String pattern = urlPatternPrefix + "/**";
		requestMappingHandlerMapping.registerMapping(requestMappingHandlerMapping.createRequestMappingInfoWithConfig(
				RequestMappingInfo.paths(pattern).methods(RequestMethod.GET)), 	this, method);
	}

	@Override
	protected MediaType getMediaType(HttpServletRequest request, Resource resource) {
		MediaType type = super.getMediaType(request, resource);
		if (type == null) {
			type = MediaType.APPLICATION_OCTET_STREAM;
		}
		return type;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	protected void moreAfterPropertiesSet() {
		// 用于重写
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setAbsPath(String absPath) {
		this.absPath = absPath;
	}

	public void setUrlPrefix(String urlPrefix) {
		this.urlPrefix = urlPrefix;
	}

	public void setRequestMatcher(RequestMatcher requestMatcher) {
		this.requestMatcher = requestMatcher;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	@Override
	public boolean canStore(MultipartFile multipartFile) {
		return true;
	}
}
