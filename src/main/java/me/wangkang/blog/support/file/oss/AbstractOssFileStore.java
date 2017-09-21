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
package me.wangkang.blog.support.file.oss;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.multipart.MultipartFile;

import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.file.CommonFile;
import me.wangkang.blog.core.file.FileStore;
import me.wangkang.blog.core.file.ImageHelper;
import me.wangkang.blog.core.file.Resize;
import me.wangkang.blog.core.file.ImageHelper.ImageInfo;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.web.Webs;

/**
 * oss文件存储抽象类
 * 
 * @author Administrator
 *
 */
public abstract class AbstractOssFileStore implements FileStore, InitializingBean {

	private final int id;
	private final String name;
	private String backupAbsPath;
	private Path backupDir;

	protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractOssFileStore.class);

	protected Resize smallResize;
	protected Resize middleResize;
	protected Resize largeResize;

	private boolean readOnly;

	public AbstractOssFileStore(int id, String name) {
		this.id = id;
		this.name = name;
	}

	@Override
	public final CommonFile store(String key, MultipartFile multipartFile) throws LogicException {
		if (!delete(key)) {
			throw new LogicException("file.store.exists", "文件" + key + "已经存在", key);
		}
		String originalFilename = multipartFile.getOriginalFilename();
		String extension = FileUtils.getFileExtension(originalFilename);
		Path tmp = FileUtils.appTemp(extension);
		try {
			Webs.save(multipartFile, tmp);
		} catch (IOException e) {
			throw new SystemException(e.getMessage(), e);
		}
		CommonFile cf = new CommonFile();

		String vkey = FileUtils.cleanPath(key);
		doUpload(vkey, tmp);

		cf.setExtension(extension);

		if (ImageHelper.isSystemAllowedImage(extension)) {
			try {
				ImageInfo ii = this.readImage(vkey);

				cf.setWidth(ii.getWidth());
				cf.setHeight(ii.getHeight());
				cf.setExtension(ii.getExtension());
			} catch (IOException e) {
				LOGGER.debug(e.getMessage(), e);
				throw new LogicException(new Message("image.corrupt", "不是正确的图片文件或者图片已经损坏"));
			}
		}

		cf.setOriginalFilename(originalFilename);
		cf.setSize(multipartFile.getSize());
		cf.setStore(id);
		return cf;
	}

	private void doUpload(String key, Path tmp) {
		Path backup = null;
		try {
			if (backupDir != null) {
				backup = FileUtils.sub(backupDir, key);
				FileUtils.forceMkdir(backup.getParent());
				FileUtils.copy(tmp, backup);
			}
			upload(key, tmp);
		} catch (IOException e) {
			if (backup != null) {
				FileUtils.deleteQuietly(backup);
			}
			throw new SystemException(e.getMessage(), e);
		}
	}

	protected abstract void upload(String key, Path file) throws IOException;

	/**
	 * 读取图片信息
	 * 
	 * @param key
	 * @return
	 * @throws IOException
	 *             图片损坏或者格式不被接受
	 */
	protected abstract ImageInfo readImage(String key) throws IOException;

	@Override
	public int id() {
		return id;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (backupAbsPath != null) {
			backupDir = Paths.get(backupAbsPath);
			FileUtils.forceMkdir(backupDir);
		}
	}

	@Override
	public final boolean deleteBatch(String key) {
		String vkey = FileUtils.cleanPath(key);
		boolean flag = doDeleteBatch(vkey);
		if (flag) {
			deleteBackup(vkey);
		}
		return flag;
	}

	@Override
	public final boolean delete(String key) {
		String vkey = FileUtils.cleanPath(key);
		boolean flag = doDelete(vkey);
		if (flag) {
			deleteBackup(vkey);
		}
		return flag;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public boolean readOnly() {
		return readOnly;
	}

	private void deleteBackup(String key) {
		if (backupDir != null) {
			Path backup = FileUtils.sub(backupDir, key);
			if (FileUtils.exists(backup)) {
				FileUtils.deleteQuietly(backup);
			}
		}
	}

	@Override
	public boolean move(String oldPath, String path) {
		String vo = FileUtils.cleanPath(oldPath);
		String vp = FileUtils.cleanPath(path);
		if (doCopy(vo, vp)) {
			if (delete(vo)) {
				return true;
			} else {
				delete(vp);
			}
		}
		return false;
	}

	@Override
	public final boolean copy(String oldPath, String path) {
		String vo = FileUtils.cleanPath(oldPath);
		String vp = FileUtils.cleanPath(path);
		if (backupDir != null) {
			Path backup = FileUtils.sub(backupDir, vo);
			if (FileUtils.exists(backup)) {
				try {
					FileUtils.copy(backup, FileUtils.sub(backupDir, vp));
				} catch (IOException e) {
					LOGGER.error("拷贝文件失败：" + e.getMessage(), e);
					return false;
				}
			}
		}
		return doCopy(vo, vp);
	}

	protected abstract boolean doCopy(String oldPath, String path);

	protected abstract boolean doDelete(String key);

	protected abstract boolean doDeleteBatch(String key);

	/**
	 * 构造缩略图信息地址
	 * 
	 * @param key
	 *            key
	 * @param resize
	 *            缩放信息，可能为null
	 * @return
	 */
	protected abstract String buildThumbnailUrl(String key, Resize resize);

	protected final boolean isSystemAllowedImage(String key) {
		return ImageHelper.isSystemAllowedImage(FileUtils.getFileExtension(key));
	}

	public void setBackupAbsPath(String backupAbsPath) {
		this.backupAbsPath = backupAbsPath;
	}

	public void setSmallResize(Resize smallResize) {
		this.smallResize = smallResize;
	}

	public void setMiddleResize(Resize middleResize) {
		this.middleResize = middleResize;
	}

	public void setLargeResize(Resize largeResize) {
		this.largeResize = largeResize;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}
}
