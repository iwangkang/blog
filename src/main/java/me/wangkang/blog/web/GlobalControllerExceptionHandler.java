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
package me.wangkang.blog.web;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.support.RequestContextUtils;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.config.UrlHelper.SpaceUrls;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.RuntimeLogicException;
import me.wangkang.blog.core.exception.SpaceNotFoundException;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.lock.Lock;
import me.wangkang.blog.core.lock.LockBean;
import me.wangkang.blog.core.lock.LockException;
import me.wangkang.blog.core.lock.LockHelper;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.security.AuthencationException;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.util.ExceptionUtils;
import me.wangkang.blog.util.UrlUtils;
import me.wangkang.blog.web.security.CsrfException;
import me.wangkang.blog.web.template.RedirectException;
import me.wangkang.blog.web.template.Template;
import me.wangkang.blog.web.template.TemplateNotFoundException;
import me.wangkang.blog.web.template.TemplateRenderException;

/**
 * 无法处理页面渲染时的异常。
 * 
 * @author mhlx
 *
 */
@Component
@ControllerAdvice
public class GlobalControllerExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalControllerExceptionHandler.class);

	@Autowired
	private UrlHelper urlHelper;

	/**
	 * tomcat client abort exception <br>
	 * 绝大部分不用记录这个异常，所以额外判断一下
	 */
	private static Class<?> clientAbortExceptionClass;

	static {
		try {
			clientAbortExceptionClass = Class.forName("org.apache.catalina.connector.ClientAbortException");
		} catch (ClassNotFoundException e) {
		}
	}

	private static final Message ERROR_403 = new Message("error.403", "权限不足");
	private static final Message ERROR_400 = new Message("error.400", "请求异常");
	private static final Message ERROR_405 = new Message("error.405", "请求方法不被允许");
	private static final Message ERROR_404 = new Message("error.404", "请求不存在");
	private static final Message ERROR_NO_ERROR_MAPPING = new Message("error.noErrorMapping", "发生了一个错误，但是没有可供显示的错误页面");

	@ExceptionHandler(AuthencationException.class)
	public String handleNoAuthencation(HttpServletRequest request, HttpServletResponse resp) throws IOException {
		if (Webs.isAjaxRequest(request)) {
			Webs.writeInfo(resp, new JsonResult(false, ERROR_403));
			return null;
		}
		resp.setStatus(HttpStatus.FORBIDDEN.value());
		// 将链接放入
		if ("get".equalsIgnoreCase(request.getMethod())) {
			request.getSession().setAttribute(Constants.LAST_AUTHENCATION_FAIL_URL, getFullUrl(request));
		}
		return getErrorRedirect(request, new ErrorInfo(ERROR_403, 403));
	}

	@ExceptionHandler(TemplateRenderException.class)
	public String handleTplRenderException(HttpServletRequest request, HttpServletResponse resp,
			TemplateRenderException e) throws IOException {
		if (!Template.isPreviewTemplate(e.getTemplateName())) {
			LOGGER.error(e.getMessage(), e);
		}
		if (Webs.isAjaxRequest(request)) {
			Webs.writeInfo(resp, new JsonResult(false, e.getRenderErrorDescription()));
			return null;
		}
		resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
		RequestContextUtils.getOutputFlashMap(request).put("description", e.getRenderErrorDescription());
		return "redirect:" + urlHelper.getUrl() + "/error/ui";
	}

	@ExceptionHandler(CsrfException.class)
	public String handleCsrfAuthencation(HttpServletRequest request, HttpServletResponse resp) throws IOException {
		if (Webs.isAjaxRequest(request)) {
			Webs.writeInfo(resp, new JsonResult(false, ERROR_403));
			return null;
		}
		resp.setStatus(HttpStatus.FORBIDDEN.value());
		return getErrorRedirect(request, new ErrorInfo(ERROR_403, 403));
	}

	@ExceptionHandler(RedirectException.class)
	public String handleRedirectException(RedirectException ex, HttpServletRequest request, HttpServletResponse resp)
			throws IOException {
		if (Webs.isAjaxRequest(request)) {
			Webs.writeInfo(resp, new RedirectJsonResult(ex.getUrl(), ex.isPermanently()));
			return null;
		}
		if (ex.isPermanently()) {
			// 301
			resp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
			resp.setHeader("Location", ex.getUrl());
			return null;
		} else {
			Message redirectMsg = ex.getRedirectMsg();
			if (redirectMsg != null) {
				RequestContextUtils.getOutputFlashMap(request).put("redirect_page_msg", redirectMsg);
			}
			return "redirect:" + ex.getUrl();
		}
	}

	@ExceptionHandler(LockException.class)
	public String handleLockException(HttpServletRequest request, HttpServletResponse response, LockException ex)
			throws IOException {
		response.setStatus(HttpStatus.FORBIDDEN.value());
		Lock lock = ex.getLock();
		String redirectUrl = getFullUrl(request);
		Message error = ex.getError();
		if (error != null) {
			RequestContextUtils.getOutputFlashMap(request).put("error", error);
		}
		// 获取空间别名
		String alias = Webs.getSpaceFromRequest(request);
		SpaceUrls urls = urlHelper.getUrlsBySpace(alias);
		LockHelper.storeLockBean(request, new LockBean(lock, ex.getLockResource(), redirectUrl, alias));
		if (alias != null) {
			return "redirect:" + urls.getUrl(new Space(alias)) + "/unlock";
		} else {
			return "redirect:" + urls.getUrl() + "/unlock";
		}
	}

	@ExceptionHandler(LogicException.class)
	public String handleLogicException(HttpServletRequest request, HttpServletResponse resp, LogicException ex)
			throws IOException {
		if (Webs.isAjaxRequest(request)) {
			Webs.writeInfo(resp, new JsonResult(false, ex.getLogicMessage()));
			return null;
		}
		return getErrorRedirect(request, new ErrorInfo(ex.getLogicMessage(), 200));
	}

	@ExceptionHandler(RuntimeLogicException.class)
	public String handleRuntimeLogicException(HttpServletRequest request, HttpServletResponse resp,
			RuntimeLogicException ex) throws IOException {
		return handleLogicException(request, resp, ex.getLogicException());
	}

	/**
	 * 空间不存在，返回主页
	 * 
	 * @param resp
	 * @throws IOException
	 */
	@ExceptionHandler(SpaceNotFoundException.class)
	public String handleSpaceNotFoundException(HttpServletResponse resp, SpaceNotFoundException ex) throws IOException {
		LOGGER.debug("空间" + ex.getAlias() + "不存在，返回主页");
		return "redirect:" + urlHelper.getUrl();
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class, TypeMismatchException.class,
			HttpMessageNotReadableException.class, BindException.class, HttpMediaTypeNotSupportedException.class,
			HttpMediaTypeNotAcceptableException.class })
	public String handlerHttpMediaTypeException(HttpServletRequest request, HttpServletResponse resp, Exception ex)
			throws IOException {
		LOGGER.debug(ex.getMessage(), ex);
		if (Webs.isAjaxRequest(request)) {
			Webs.writeInfo(resp, new JsonResult(false, ERROR_400));
			return null;
		}
		resp.setStatus(HttpStatus.BAD_REQUEST.value());
		return getErrorRedirect(request, new ErrorInfo(ERROR_400, 400));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseBody
	public JsonResult handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		BindingResult result = ex.getBindingResult();
		List<ObjectError> errors = result.getAllErrors();
		for (ObjectError error : errors) {
			return new JsonResult(false, new Message(error.getCode(), error.getDefaultMessage(), error.getArguments()));
		}
		throw new SystemException("抛出了MethodArgumentNotValidException，但没有发现任何错误");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public String handleHttpRequestMethodNotSupportedException(HttpServletRequest request, HttpServletResponse resp,
			HttpRequestMethodNotSupportedException ex) throws IOException {
		if (Webs.isAjaxRequest(request)) {
			Webs.writeInfo(resp, new JsonResult(false, ERROR_405));
			return null;
		}
		resp.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
		return getErrorRedirect(request, new ErrorInfo(ERROR_405, 405));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public String handleMaxUploadSizeExceededException(HttpServletRequest req, HttpServletResponse resp,
			MaxUploadSizeExceededException e) throws IOException {
		if (Webs.isAjaxRequest(req)) {
			Webs.writeInfo(resp, new JsonResult(false, new Message("upload.overlimitsize",
					"超过允许的最大上传文件大小：" + e.getMaxUploadSize() + "字节", e.getMaxUploadSize())));
			return null;
		}
		return getErrorRedirect(req, new ErrorInfo(new Message("upload.overlimitsize",
				"超过允许的最大上传文件大小：" + e.getMaxUploadSize() + "字节", e.getMaxUploadSize()), 200));
	}

	@ExceptionHandler(MultipartException.class)
	public void handleMultipartException(MultipartException ex, HttpServletRequest req, HttpServletResponse resp) {
		//
	}

	@ExceptionHandler(value = { NoHandlerFoundException.class, TemplateNotFoundException.class })
	public String noHandlerFoundException(HttpServletRequest request, HttpServletResponse resp) throws IOException {
		return handleNotFound(request, resp);
	}

	private String handleNotFound(HttpServletRequest request, HttpServletResponse resp) throws IOException {
		if (Webs.isAjaxRequest(request)) {
			Webs.writeInfo(resp, new JsonResult(false, ERROR_404));
			return null;
		}
		// 防止找不到错误页面重定向
		String mapping = request.getServletPath();
		String space = Webs.getSpaceFromRequest(request);
		SpaceUrls urls = urlHelper.getUrlsBySpace(space);
		String redirectMapping = "";
		if (space != null) {
			redirectMapping = "/space/" + space + "/error";
		} else {
			redirectMapping = "/error";
		}
		if (redirectMapping.equals(mapping)) {
			Webs.writeInfo(resp, new JsonResult(false, ERROR_NO_ERROR_MAPPING));
			return null;
		} else {
			RequestContextUtils.getOutputFlashMap(request).put(Constants.ERROR, new ErrorInfo(ERROR_404, 404));
			return "redirect:" + urls.getCurrentUrl() + "/error";
		}
	}

	@ExceptionHandler(value = Exception.class)
	public String defaultHandler(HttpServletRequest request, HttpServletResponse resp, Exception e) throws IOException {
		if (clientAbortExceptionClass == null
				|| !ExceptionUtils.getFromChain(e, clientAbortExceptionClass).isPresent()) {
			LOGGER.error(e.getMessage(), e);
		}
		if (resp.isCommitted()) {
			return null;
		}
		if (Webs.isAjaxRequest(request)) {
			Webs.writeInfo(resp, new JsonResult(false, Constants.SYSTEM_ERROR));
			return null;
		}
		resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
		return getErrorRedirect(request, new ErrorInfo(Constants.SYSTEM_ERROR, 500));
	}

	private String getFullUrl(HttpServletRequest request) {
		return UrlUtils.buildFullRequestUrl(request);
	}

	private String getErrorRedirect(HttpServletRequest request, ErrorInfo error) {
		if (error != null) {
			RequestContextUtils.getOutputFlashMap(request).put(Constants.ERROR, error);
		}
		// 这里必须通过Environment.hasSpace()来判断，而不能通过Webs.getSpace(request) !=
		// null来判断
		// 因为如果空间是私人的，这里会造成循坏重定向
		if (Environment.hasSpace()) {
			return "redirect:" + urlHelper.getUrl() + "/space/" + Environment.getSpaceAlias() + "/error";
		} else {
			return "redirect:" + urlHelper.getUrl() + "/error";
		}
	}

	public static final class ErrorInfo implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private final Message message;
		private final int code;

		private ErrorInfo(Message message, int code) {
			super();
			this.message = message;
			this.code = code;
		}

		public Message getMessage() {
			return message;
		}

		public int getCode() {
			return code;
		}

	}

	public static final class RedirectJsonResult extends JsonResult {

		private final String url;
		private final boolean permanently;

		private RedirectJsonResult(String url, boolean permanently) {
			super(true);
			this.url = url;
			this.permanently = permanently;
		}

		public boolean isPermanently() {
			return permanently;
		}

		public String getUrl() {
			return url;
		}
	}

}