package me.wangkang.blog.support.file.oss;
///*
// * Copyright 2017 wangkang.me
// * 
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// * 
// *     http://www.apache.org/licenses/LICENSE-2.0
// * 
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package me.wangkang.blog.support.file.oss;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.Date;
//import java.util.List;
//import java.util.Objects;
//import java.util.Optional;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.event.ContextClosedEvent;
//import org.springframework.context.event.EventListener;
//import org.springframework.core.io.UrlResource;
//import org.springframework.web.multipart.MultipartFile;
//import org.springframework.web.util.UriComponents;
//import org.springframework.web.util.UriComponentsBuilder;
//
//import com.aliyun.oss.ClientException;
//import com.aliyun.oss.OSSClient;
//import com.aliyun.oss.OSSException;
//import com.aliyun.oss.internal.OSSUtils;
//import com.aliyun.oss.model.ListObjectsRequest;
//import com.aliyun.oss.model.OSSObjectSummary;
//import com.aliyun.oss.model.ObjectListing;
//
//import me.wangkang.blog.core.config.UrlHelper;
//import me.wangkang.blog.core.exception.SystemException;
//import me.wangkang.blog.core.file.Resize;
//import me.wangkang.blog.core.file.ThumbnailUrl;
//import me.wangkang.blog.core.file.ImageHelper.ImageInfo;
//import me.wangkang.blog.util.Jsons;
//import me.wangkang.blog.util.Jsons.ExpressionExecutor;
//import me.wangkang.blog.util.Resources;
//import me.wangkang.blog.util.UrlUtils;
//
///**
// * 阿里云的
// * 
// * @author Administrator
// *
// */
//public class AliyunFileStore extends AbstractOssFileStore {
//
//	@Autowired
//	private UrlHelper urlHelper;
//
//	private static final long PRIVATE_DOWNLOAD_URL_EXPIRES = 3600L;
//	private static final String DEFAULT_STYLE_PARAM = "?x-oss-process=style/";
//	private static final String RESIZE_PARAM = "?x-oss-process=image/resize";
//	private static final String READ_IMAGE_PARAM = "?x-oss-process=image/info";
//
//	/**
//	 * 访问的前缀，如果不指定，则为 bucket + '.' +endpoint
//	 */
//	private String prefix;
//
//	private boolean sourceProtected;// 源图保护
//
//	/**
//	 * 私有空间
//	 * <p>
//	 * <b>如果为true，原图保护将不起作用</b>
//	 * </p>
//	 */
//	private boolean secret;
//
//	/**
//	 * 样式分割符，
//	 */
//	private Character styleSplitChar;
//
//	/**
//	 * 样式，开启原图保护之后必须提供
//	 */
//	private String style;
//
//	private long privateDownloadUrlExpires = PRIVATE_DOWNLOAD_URL_EXPIRES;
//
//	private OSSClient ossClient;
//
//	private String host;
//
//	private final String endpoint;
//	private final String bucket;
//
//	public AliyunFileStore(int id, String name, String ak, String sk, String endpoint, String bucket) {
//		super(id, name);
//		this.ossClient = new OSSClient(endpoint, ak, sk);
//		this.endpoint = endpoint;
//		this.bucket = bucket;
//	}
//
//	@EventListener
//	public void handleCloseEvent(ContextClosedEvent event) {
//		this.ossClient.shutdown();
//	}
//
//	@Override
//	public String getUrl(String key) {
//		if (secret) {
//			return privateUrl(key);
//		}
//		String url;
//		if (isSystemAllowedImage(key) && sourceProtected) {
//			url = sourceProtectedUrl(key);
//		} else {
//			url = prefix + key;
//		}
//		return url;
//	}
//
//	@Override
//	public Optional<ThumbnailUrl> getThumbnailUrl(String key) {
//		if (isSystemAllowedImage(key)) {
//			if (secret) {
//				String url = privateUrl(key);
//				return Optional.of(new AliyunThumbnailUrl(url, url, url, key));
//			} else if (sourceProtected) {
//				String url = sourceProtectedUrl(key);
//				return Optional.of(new AliyunThumbnailUrl(url, url, url, key));
//			} else {
//				String small = buildThumbnailUrl(key, smallResize);
//				String middle = buildThumbnailUrl(key, middleResize);
//				String large = buildThumbnailUrl(key, largeResize);
//				return Optional.of(new AliyunThumbnailUrl(small, middle, large, key));
//			}
//		}
//		return Optional.empty();
//	}
//
//	private final class AliyunThumbnailUrl extends ThumbnailUrl {
//
//		/**
//		 * 
//		 */
//		private static final long serialVersionUID = 1L;
//		private final String key;
//
//		private AliyunThumbnailUrl(String small, String middle, String large, String key) {
//			super(small, middle, large);
//			this.key = key;
//		}
//
//		@Override
//		public String getThumbUrl(int width, int height, boolean keepRatio) {
//			return buildThumbnailUrl(key, new Resize(width, height, keepRatio));
//		}
//
//		@Override
//		public String getThumbUrl(int size) {
//			return buildThumbnailUrl(key, new Resize(size));
//		}
//
//	}
//
//	@Override
//	public boolean canStore(MultipartFile multipartFile) {
//		return true;
//	}
//
//	@Override
//	public boolean doCopy(String oldPath, String path) {
//		try {
//			ossClient.copyObject(bucket, oldPath, bucket, path);
//			return true;
//		} catch (OSSException | ClientException e) {
//			LOGGER.error(e.getMessage(), e);
//		}
//		return false;
//	}
//
//	/**
//	 * {@inheritDoc}
//	 * 
//	 * 阿里云上传key不能以 '/'开头
//	 * 
//	 * @see OSSUtils#validateObjectKey(String)
//	 */
//	@Override
//	protected void upload(String key, Path file) throws IOException {
//		ossClient.putObject(bucket, key, Files.newInputStream(file));
//	}
//
//	@Override
//	protected boolean doDelete(String key) {
//		try {
//			ossClient.deleteObject(bucket, key);
//		} catch (OSSException | ClientException e) {
//			LOGGER.error(e.getMessage(), e);
//			return false;
//		}
//		return true;
//	}
//
//	/**
//	 * {@link https://help.aliyun.com/document_detail/32015.html?spm=5176.doc32011.2.7.O46qUc#h2-u5217u51FAu5B58u50A8u7A7Au95F4u4E2Du7684u6587u4EF6}
//	 * {@link https://github.com/aliyun/aliyun-oss-java-sdk/blob/master/src/samples/ListObjectsSample.java?spm=5176.doc32015.2.2.fIAJ39&file=ListObjectsSample.java}
//	 */
//	@Override
//	protected boolean doDeleteBatch(String key) {
//		String nextMarker = null;
//		ObjectListing objectListing;
//		List<OSSObjectSummary> sums;
//		int maxKeys = 100;
//		try {
//			do {
//				objectListing = ossClient.listObjects(
//						new ListObjectsRequest(bucket).withPrefix(key).withMarker(nextMarker).withMaxKeys(maxKeys));
//
//				sums = objectListing.getObjectSummaries();
//
//				for (OSSObjectSummary s : sums) {
//					ossClient.deleteObject(bucket, s.getKey());
//				}
//
//				nextMarker = objectListing.getNextMarker();
//
//			} while (objectListing.isTruncated());
//		} catch (OSSException | ClientException e) {
//			LOGGER.error(e.getMessage(), e);
//			return false;
//		}
//		return true;
//	}
//
//	@Override
//	public void afterPropertiesSet() throws Exception {
//		super.afterPropertiesSet();
//
//		if (this.prefix != null) {
//			if (!UrlUtils.isAbsoluteUrl(prefix)) {
//				throw new SystemException("prefix:" + prefix + "必须是一个绝对路径");
//			}
//		} else {
//			this.prefix = urlHelper.getUrlConfig().getSchema() + "://" + bucket + '.' + endpoint;
//		}
//
//		UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(prefix).build();
//		this.host = uriComponents.getHost();
//
//		if (!this.prefix.endsWith("/")) {
//			this.prefix = prefix + '/';
//		}
//
//		if (secret) {
//			sourceProtected = false;
//		}
//
//		if (sourceProtected && style == null) {
//			throw new SystemException("开启原图保护之后必须提供样式");
//		}
//	}
//
//	@Override
//	protected String buildThumbnailUrl(String key, Resize resize) {
//		if (resize == null) {
//			return prefix + key;
//		} else if (resize.getSize() != null) {
//			return prefix + key + RESIZE_PARAM + ",s_" + resize.getSize();
//		} else if (resize.getWidth() == 0 && resize.getHeight() == 0) {
//			return prefix + key;
//		} else if (resize.getWidth() == 0) {
//			return prefix + key + ",h_" + resize.getHeight();
//		} else if (resize.getHeight() == 0) {
//			return prefix + key + ",w_" + resize.getWidth();
//		} else if (resize.isKeepRatio()) {
//			return prefix + key + ",w_" + resize.getWidth() + ",h_" + resize.getHeight();
//		} else {
//			return prefix + key + ",w_" + resize.getWidth() + ",h_" + resize.getHeight() + ",m_fixed";
//		}
//	}
//
//	@Override
//	protected ImageInfo readImage(String key) throws IOException {
//		String result = Resources.readResourceToString(new UrlResource(prefix + key + READ_IMAGE_PARAM));
//		ExpressionExecutor executor = Jsons.readJson(result);
//		if (executor.isNull()) {
//			throw new IOException("无法将结果转化json信息:" + result);
//		}
//		try {
//			Integer height = Integer.parseInt(executor.execute("ImageHeight->value"));
//			Integer width = Integer.parseInt(executor.execute("ImageWidth->value"));
//			String format = executor.execute("Format->value");
//			Objects.requireNonNull(format);
//			return new ImageInfo(width, height, format);
//		} catch (Exception e) {
//			throw new IOException("获取图片信息失败:" + result,e);
//		}
//	}
//
//	private String sourceProtectedUrl(String key) {
//		StringBuilder sb = new StringBuilder();
//		sb.append(prefix).append(key);
//		sb.append(styleSplitChar == null ? DEFAULT_STYLE_PARAM : styleSplitChar);
//		sb.append(style);
//		return sb.toString();
//	}
//
//	private String privateUrl(String key) {
//		Date date = new Date(new Date().getTime() + privateDownloadUrlExpires * 1000);
//		/**
//		 * 这里获取到的地址 为endpoint，替换为prefix的host
//		 */
//		UriComponents uriComponents = UriComponentsBuilder
//				.fromHttpUrl(ossClient.generatePresignedUrl(bucket, key, date).toString()).host(host).build();
//		return uriComponents.toString();
//	}
//
//	public void setPrefix(String prefix) {
//		this.prefix = prefix;
//	}
//
//	public void setSourceProtected(boolean sourceProtected) {
//		this.sourceProtected = sourceProtected;
//	}
//
//	public void setSecret(boolean secret) {
//		this.secret = secret;
//	}
//
//	public void setStyleSplitChar(Character styleSplitChar) {
//		this.styleSplitChar = styleSplitChar;
//	}
//
//	public void setStyle(String style) {
//		this.style = style;
//	}
//
//	public void setPrivateDownloadUrlExpires(long privateDownloadUrlExpires) {
//		this.privateDownloadUrlExpires = privateDownloadUrlExpires;
//	}
//}
