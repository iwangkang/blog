package me.wangkang.blog.web.template.thymeleaf;

import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.View;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.web.template.ParseConfig;
import me.wangkang.blog.web.template.ReadOnlyResponse;
import me.wangkang.blog.web.template.RenderResult;
import me.wangkang.blog.web.template.TemplateRender;

@Component
public class ThymeleafViewResolver extends org.thymeleaf.spring4.view.ThymeleafViewResolver {

	@Autowired
	private TemplateRender templateRender;

	@Override
	protected View loadView(String viewName, Locale locale) throws Exception {
		return new _View(viewName);
	}

	private final class _View implements View {

		private final String templateName;

		public _View(String templateName) {
			super();
			this.templateName = templateName;
		}

		@Override
		public String getContentType() {
			return "text/html";
		}

		@Override
		public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
				throws Exception {
			RenderResult rendered = templateRender.doRender(templateName, model, request,
					new ReadOnlyResponse(response), new ParseConfig());

			response.setContentType(getContentType());
			response.setCharacterEncoding(Constants.CHARSET.name());
			response.getWriter().write(rendered.getContent());
			response.getWriter().flush();

		}

	}

}
