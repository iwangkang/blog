package me.wangkang.blog.web.template;

import java.util.Optional;

import org.springframework.web.method.HandlerMethod;

import me.wangkang.blog.core.exception.SystemException;

public class TemplateUtils {

	private TemplateUtils() {
		super();
	}

	public static Optional<TemplateController> getTemplateController(Object handler) {
		if (handler instanceof HandlerMethod) {
			Object bean = ((HandlerMethod) handler).getBean();
			if (bean instanceof TemplateController) {
				return Optional.of((TemplateController) bean);
			}
		}
		return Optional.empty();
	}
	
	public static TemplateController getRequiredTemplateController(Object handler){
		return getTemplateController(handler).orElseThrow(()->new SystemException("无法转化为TemplateController"));
	}

}
