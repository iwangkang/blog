package me.wangkang.blog.core.vo;

import me.wangkang.blog.core.entity.CommentModule;

public class ModuleCommentCount {
	private CommentModule module;
	private Integer comments;

	public CommentModule getModule() {
		return module;
	}

	public void setModule(CommentModule module) {
		this.module = module;
	}

	public Integer getComments() {
		return comments;
	}

	public void setComments(Integer comments) {
		this.comments = comments;
	}

	public Integer getModuleId() {
		return module.getId();
	}

}