package me.wangkang.blog.web.template;

import org.springframework.http.MediaType;

public class RenderResult {

	private final MediaType type;
	private final String content;

	public RenderResult(MediaType type, String content) {
		super();
		this.type = type;
		this.content = content;
	}

	public MediaType getType() {
		return type;
	}

	public String getContent() {
		return content;
	}

}
