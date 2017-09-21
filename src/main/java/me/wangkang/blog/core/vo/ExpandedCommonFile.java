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
package me.wangkang.blog.core.vo;

import me.wangkang.blog.core.file.CommonFile;
import me.wangkang.blog.core.file.ThumbnailUrl;

/**
 * 拓展的common file
 * 
 * @author Administrator
 *
 */
public class ExpandedCommonFile extends CommonFile {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ThumbnailUrl thumbnailUrl;
	private String url;
	private String downloadUrl;

	public ExpandedCommonFile() {
		super();
	}

	public ExpandedCommonFile(CommonFile cf) {
		super(cf);
	}

	public ThumbnailUrl getThumbnailUrl() {
		return thumbnailUrl;
	}

	public void setThumbnailUrl(ThumbnailUrl thumbnailUrl) {
		this.thumbnailUrl = thumbnailUrl;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public void setDownloadUrl(String downloadUrl) {
		this.downloadUrl = downloadUrl;
	}

}
