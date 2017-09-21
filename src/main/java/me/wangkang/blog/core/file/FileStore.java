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
package me.wangkang.blog.core.file;

import java.io.IOException;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import me.wangkang.blog.core.exception.LogicException;

/**
 * 文件存储器
 * <p>
 * <b>执行文件操作的时候(写入|删除|拷贝|移动)等，可能需要额外的同步</b>
 * </p>
 * 
 * @author Administrator
 *
 */
public interface FileStore {

	/**
	 * 储存文件
	 * 
	 * @param key
	 *            文件路径
	 * @param multipartFile
	 *            文件
	 * @return 尺寸成功后的文件信息
	 * @throws LogicException
	 * @throws IOException
	 */
	CommonFile store(String key, MultipartFile multipartFile) throws LogicException;

	/**
	 * 存储器ID
	 * 
	 * @return
	 */
	int id();

	/**
	 * 删除物理文件
	 * 
	 * @param key
	 *            文件路径
	 * @return true:删除成功|文件不存在，无需删除 false :删除失败(可能占用中)
	 */
	boolean delete(String key);

	/**
	 * 删除文件夹下物理文件
	 * 
	 * @param key
	 *            文件路径
	 * @return true:如果文件夹不存在或者全部文件删除成功
	 */
	boolean deleteBatch(String key);

	/**
	 * 获取文件的访问路径
	 * 
	 * @param key
	 *            文件路径
	 * @return
	 */
	String getUrl(String key);

	/**
	 * 获取缩略图路径
	 * 
	 * @param key
	 *            文件路径
	 * @return
	 */
	Optional<ThumbnailUrl> getThumbnailUrl(String key);

	/**
	 * 是否能够存储该文件
	 * 
	 * @param multipartFile
	 * @return
	 */
	boolean canStore(MultipartFile multipartFile);

	/**
	 * 存储器名称
	 * 
	 * @return
	 */
	String name();

	/**
	 * 是否只读
	 * 
	 * @return
	 */
	boolean readOnly();

	/**
	 * 拷贝<b>文件</b>
	 * <p>
	 * 如果目标地址存在文件，则覆盖
	 * </p>
	 * 
	 * @param oldPath
	 *            源路径
	 * @param path
	 *            现路径
	 * @return 拷贝成功|失败
	 */
	boolean copy(String oldPath, String path);

	/**
	 * 移动<b>文件</b>
	 * <p>
	 * 如果目标地址存在文件，则覆盖
	 * </p>
	 * 
	 * @param oldPath
	 *            原路径
	 * @param path
	 *            新路径
	 * @return
	 */
	boolean move(String oldPath, String path);

}
