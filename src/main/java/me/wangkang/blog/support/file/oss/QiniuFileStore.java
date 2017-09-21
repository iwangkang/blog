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
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//
//import org.springframework.core.io.UrlResource;
//import org.springframework.web.multipart.MultipartFile;
//
//import com.qiniu.common.QiniuException;
//import com.qiniu.http.Response;
//import com.qiniu.storage.BucketManager;
//import com.qiniu.storage.BucketManager.Batch;
//import com.qiniu.storage.UploadManager;
//import com.qiniu.storage.model.FileInfo;
//import com.qiniu.storage.model.FileListing;
//import com.qiniu.util.Auth;
//
//import me.wangkang.blog.core.exception.SystemException;
//import me.wangkang.blog.core.file.ImageHelper.ImageInfo;
//import me.wangkang.blog.core.file.Resize;
//import me.wangkang.blog.core.file.ThumbnailUrl;
//import me.wangkang.blog.core.service.FileService;
//import me.wangkang.blog.util.Jsons;
//import me.wangkang.blog.util.Jsons.ExpressionExecutor;
//import me.wangkang.blog.util.Resources;
//import me.wangkang.blog.util.UrlUtils;
//import me.wangkang.blog.util.Validators;
//
///**
// * 提供了对七牛云存储的简单操作，必须引入七牛云的sdk:
// * {@link http://developer.qiniu.com/code/v7/sdk/java.html}
// * <p>
// * 如果提供了backupAbsPath，那么上传时同时也会将文件备份至该目录下，通过new File(backAbsPath,key)可以定位备份文件
// * </p>
// * <p>
// * 如果空间为私有空间，请设置secret为true，这样文件的路径将会增加必要的token信息
// * </p>
// * 
// * @author Administrator
// *
// */
//public class QiniuFileStore extends AbstractOssFileStore {
//
//	private static final long PRIVATE_DOWNLOAD_URL_EXPIRES = 3600L;
//
//	private String urlPrefix;// 外链域名
//	private boolean secret;// 私人空间
//	private long privateDownloadUrlExpires = PRIVATE_DOWNLOAD_URL_EXPIRES;
//	private Character styleSplitChar;// 样式分隔符
//	private boolean sourceProtected;// 原图保护
//	private String style;// 样式
//
//	/**
//	 * 七牛云推荐的分页条数
//	 */
//	private static final int RECOMMEND_LIMIT = 100;
//
//	private final String bucket;
//	private final Auth auth;
//
//	private static final int FILE_NOT_EXISTS_ERROR_CODE = 612;// 文件不存在错误码
//
//	private static final String IMAGE_INFO_PARAM = "?imageInfo";
//
//	public QiniuFileStore(int id, String name, String ak, String sk, String bucket) {
//		super(id, name);
//		this.auth = Auth.create(ak, sk);
//		this.bucket = bucket;
//	}
//
//	@Override
//	protected void upload(String key, Path file) throws IOException {
//		UploadManager uploadManager = new UploadManager();
//		try {
//			Response resp = uploadManager.put(file.toFile(), key, getUpToken());
//			if (!resp.isOK()) {
//				throw new IOException("七牛云上传失败，异常信息:" + resp.toString() + ",响应信息:" + resp.bodyString());
//			}
//		} catch (QiniuException e) {
//			Response r = e.response;
//			try {
//				throw new IOException("七牛云上传失败，异常信息:" + r.toString() + ",响应信息:" + r.bodyString(), e);
//			} catch (QiniuException e1) {
//				LOGGER.debug(e1.getMessage(), e1);
//			}
//		}
//	}
//
//	@Override
//	protected boolean doDelete(String key) {
//		boolean flag = false;
//		BucketManager bucketManager = new BucketManager(auth);
//		try {
//			bucketManager.delete(bucket, key);
//			flag = true;
//		} catch (QiniuException e) {
//			Response r = e.response;
//			if (r.statusCode == FILE_NOT_EXISTS_ERROR_CODE) {
//				flag = true;
//			} else {
//				try {
//					LOGGER.error("七牛云删除失败，异常信息:" + r.toString() + ",响应信息:" + r.bodyString(), e);
//				} catch (QiniuException e1) {
//					LOGGER.debug(e1.getMessage(), e1);
//				}
//			}
//		}
//		return flag;
//	}
//
//	@Override
//	public String getUrl(String key) {
//		String url = urlPrefix + key;
//		if (secret) {
//			return auth.privateDownloadUrl(url);
//		}
//		if (isSystemAllowedImage(key) && sourceProtected) {
//			return url + styleSplitChar + style;
//		}
//		return url;
//	}
//
//	@Override
//	public Optional<ThumbnailUrl> getThumbnailUrl(String key) {
//
//		if (isSystemAllowedImage(key)) {
//			if (sourceProtected) {
//				// 只能采用样式访问
//				String url = urlPrefix + key + styleSplitChar + style;
//				return Optional.of(new QiniuThumbnailUrl(url, url, url, key));
//			} else {
//				String small = buildThumbnailUrl(key, smallResize);
//				String middle = buildThumbnailUrl(key, middleResize);
//				String large = buildThumbnailUrl(key, largeResize);
//				if (secret) {
//					return Optional.of(new QiniuThumbnailUrl(auth.privateDownloadUrl(small),
//							auth.privateDownloadUrl(middle), auth.privateDownloadUrl(large), key));
//				} else {
//					return Optional.of(new QiniuThumbnailUrl(small, middle, large, key));
//				}
//			}
//		}
//		return Optional.empty();
//	}
//
//	private final class QiniuThumbnailUrl extends ThumbnailUrl {
//
//		/**
//		 * 
//		 */
//		private static final long serialVersionUID = 1L;
//		private final String key;
//
//		private QiniuThumbnailUrl(String small, String middle, String large, String key) {
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
//	public boolean doDeleteBatch(String key) {
//		try {
//			List<String> keys = new ArrayList<>();
//			BucketManager bucketManager = new BucketManager(auth);
//			FileListing fileListing = bucketManager.listFiles(bucket, key + FileService.SPLIT_CHAR, null,
//					RECOMMEND_LIMIT, null);
//
//			do {
//				FileInfo[] items = fileListing.items;
//				if (items != null && items.length > 0) {
//					for (FileInfo fileInfo : items) {
//						keys.add(fileInfo.key);
//					}
//				}
//				fileListing = bucketManager.listFiles(bucket, key + FileService.SPLIT_CHAR, fileListing.marker,
//						RECOMMEND_LIMIT, null);
//			} while (!fileListing.isEOF());
//
//			if (keys.isEmpty()) {
//				return true;
//			}
//
//			Batch batch = new Batch();
//			batch.delete(bucket, keys.toArray(new String[] {}));
//			return bucketManager.batch(batch).isOK();
//		} catch (QiniuException e) {
//			// 捕获异常信息
//			Response r = e.response;
//			LOGGER.error(r.toString(), e);
//		}
//		return false;
//	}
//
//	@Override
//	protected ImageInfo readImage(String key) throws IOException {
//		String json = Resources.readResourceToString(new UrlResource(urlPrefix + key + IMAGE_INFO_PARAM));
//		ExpressionExecutor executor = Jsons.readJson(json);
//		if (executor.isNull()) {
//			throw new IOException("无法将结果转化为json信息:" + json);
//		}
//		try {
//			String format = executor.execute("format");
//			Integer width = Integer.parseInt(executor.execute("width"));
//			Integer height = Integer.parseInt(executor.execute("height"));
//			return new ImageInfo(width, height, format);
//		} catch (Exception e) {
//			throw new IOException("获取图片信息失败:" + json,e);
//		}
//	}
//
//	@Override
//	public boolean doCopy(String oldPath, String path) {
//		try {
//			new BucketManager(auth).copy(bucket, oldPath, bucket, path);
//			return true;
//		} catch (QiniuException e) {
//			try {
//				Response r = e.response;
//				LOGGER.error("七牛云拷贝文件失败，异常信息:" + r.toString() + ",响应信息:" + r.bodyString(), e);
//			} catch (QiniuException e1) {
//				LOGGER.debug(e1.getMessage(), e1);
//			}
//		}
//		return false;
//	}
//
//	@Override
//	public void afterPropertiesSet() throws Exception {
//		super.afterPropertiesSet();
//		if (Validators.isEmptyOrNull(bucket, true)) {
//			throw new SystemException("Bucket不能为空");
//		}
//		if (Validators.isEmptyOrNull(urlPrefix, true)) {
//			throw new SystemException("外链域名不能为空");
//		}
//		if (!UrlUtils.isAbsoluteUrl(urlPrefix)) {
//			throw new SystemException("外链域名必须是一个绝对路径");
//		}
//		if (!urlPrefix.endsWith("/")) {
//			urlPrefix += "/";
//		}
//		if (privateDownloadUrlExpires < 0) {
//			privateDownloadUrlExpires = PRIVATE_DOWNLOAD_URL_EXPIRES;
//		}
//
//		if (sourceProtected) {
//			if (style == null) {
//				throw new SystemException("开启了原图保护之后请指定一个默认的样式名");
//			}
//			if (styleSplitChar == null) {
//				styleSplitChar = '-';
//			}
//		}
//	}
//
//	/**
//	 * {@link Resize#isKeepRatio()}设置无效
//	 * 
//	 * @return
//	 */
//	protected Optional<String> buildResizeParam(Resize resize) {
//		String result = null;
//		if (resize != null) {
//			if (resize.getSize() != null) {
//				result = "imageView2/2/w/" + resize.getSize() + "/h/" + resize.getSize();
//			} else if (resize.getWidth() == 0 && resize.getHeight() == 0) {
//				result = null;
//			} else if (resize.getWidth() == 0) {
//				result = "imageView2/2/h/" + resize.getHeight();
//			} else if (resize.getHeight() == 0) {
//				result = "imageView2/2/w/" + resize.getWidth();
//			} else {
//				result = "imageView2/2/w/" + resize.getWidth() + "/h/" + resize.getHeight();
//			}
//		}
//		return Optional.ofNullable(result);
//	}
//
//	@Override
//	protected String buildThumbnailUrl(String key, Resize resize) {
//		return urlPrefix + key + buildResizeParam(resize).map(param -> "?" + param).orElse("");
//	}
//
//	// 简单上传，使用默认策略，只需要设置上传的空间名就可以了
//	protected String getUpToken() {
//		return auth.uploadToken(bucket);
//	}
//
//	public void setUrlPrefix(String urlPrefix) {
//		this.urlPrefix = urlPrefix;
//	}
//
//	public void setSecret(boolean secret) {
//		this.secret = secret;
//	}
//
//	public void setPrivateDownloadUrlExpires(long privateDownloadUrlExpires) {
//		this.privateDownloadUrlExpires = privateDownloadUrlExpires;
//	}
//
//	public void setStyleSplitChar(Character styleSplitChar) {
//		this.styleSplitChar = styleSplitChar;
//	}
//
//	public void setSourceProtected(boolean sourceProtected) {
//		this.sourceProtected = sourceProtected;
//	}
//
//	public void setStyle(String style) {
//		this.style = style;
//	}
//
//	@Override
//	public boolean canStore(MultipartFile multipartFile) {
//		return true;// can store every file
//	}
//
//}
