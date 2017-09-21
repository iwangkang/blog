package me.wangkang.blog.web.template;

import javax.servlet.http.HttpServletRequest;

import me.wangkang.blog.web.TemplateView;

/**
 * 将路径模板转化为一个Controller
 * 
 * @author mhlx
 *
 */
public abstract class TemplateController {

	private final TemplateView templateView;
	private final String templateName;
	private final String path;
	private final Integer id;

	protected TemplateController(Integer id, String templateName, String path) {
		super();
		this.templateView = new TemplateView(templateName);
		this.templateName = templateName;
		this.path = path;
		this.id = id;
	}

	public abstract TemplateView handleRequest(HttpServletRequest request);

	/**
	 * 返回模板ID，id越大，优先级越高
	 * 
	 * @return
	 */
	public Integer getId() {
		return id;
	}

	public TemplateView getTemplateView() {
		return templateView;
	}

	public String getTemplateName() {
		return templateName;
	}

	public String getPath() {
		return path;
	}
}
