/*
 * Copyright 2016 wangkang.me
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
package me.wangkang.blog.core.config;

import java.util.concurrent.TimeUnit;

import com.google.gson.annotations.Expose;

import me.wangkang.blog.core.entity.Editor;
import me.wangkang.blog.core.vo.Limit;

/**
 * 评论配置
 * 
 * @author Administrator
 *
 */
public class CommentConfig {

	private Editor editor;// 编辑器
	@Expose(serialize = false, deserialize = true)
	private Integer limitCount;
	@Expose(serialize = false, deserialize = true)
	private Integer limitSec;
	private Boolean check;// 审核
	private Integer pageSize;// 每页显示数量

	public CommentConfig() {
		super();
	}

	public CommentConfig(CommentConfig source) {
		this.editor = source.editor;
		this.check = source.check;
		this.limitCount = source.limitCount;
		this.limitSec = source.limitSec;
		this.pageSize = source.pageSize;
	}

	public Integer getLimitSec() {
		return limitSec;
	}

	public void setLimitSec(Integer limitSec) {
		this.limitSec = limitSec;
	}

	public Integer getLimitCount() {
		return limitCount;
	}

	public void setLimitCount(Integer limitCount) {
		this.limitCount = limitCount;
	}

	public Limit getLimit() {
		return new Limit(limitCount, limitSec, TimeUnit.SECONDS);
	}

	public Boolean getCheck() {
		return check;
	}

	public void setCheck(Boolean check) {
		this.check = check;
	}

	public Integer getPageSize() {
		return pageSize;
	}

	public void setPageSize(Integer pageSize) {
		this.pageSize = pageSize;
	}

	public Editor getEditor() {
		return editor;
	}

	public void setEditor(Editor editor) {
		this.editor = editor;
	}
}