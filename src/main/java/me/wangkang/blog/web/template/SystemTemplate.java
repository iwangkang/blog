package me.wangkang.blog.web.template;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;

import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.Resources;
import me.wangkang.blog.util.Validators;

/**
 * 系统内置模板
 * 
 * @author mhlx
 *
 */
public final class SystemTemplate implements Template {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final String SYSTEM_PREFIX = TEMPLATE_PREFIX + "System" + SPLITER;
	private final String path;
	private String template;
	private String templateName;

	private SystemTemplate(SystemTemplate systemTemplate) {
		this.path = systemTemplate.path;
		this.template = systemTemplate.template;
	}

	public SystemTemplate(String path, String templateClassPath) {
		super();
		this.path = path;
		try {
			this.template = Resources.readResourceToString(new ClassPathResource(templateClassPath));
		} catch (IOException e) {
			throw new SystemException(e.getMessage(), e);
		}
	}

	@Override
	public boolean isRoot() {
		return true;
	}

	@Override
	public String getTemplate() {
		return template;
	}

	@Override
	public String getTemplateName() {
		if (templateName == null) {
			templateName = SYSTEM_PREFIX + FileUtils.cleanPath(path);
		}
		return templateName;
	}

	@Override
	public Template cloneTemplate() {
		return new SystemTemplate(this);
	}

	@Override
	public boolean isCallable() {
		return false;
	}

	@Override
	public boolean equalsTo(Template other) {
		if (Validators.baseEquals(this, other)) {
			SystemTemplate rhs = (SystemTemplate) other;
			return Objects.equals(this.path, rhs.path);
		}
		return false;
	}

	public String getPath() {
		return path;
	}

	public static boolean isSystemTemplate(String templateName) {
		return templateName != null && templateName.startsWith(SYSTEM_PREFIX);
	}

	public static Optional<String> getPath(String templateName) {
		return isSystemTemplate(templateName) ? Optional.of(templateName.substring(SYSTEM_PREFIX.length()))
				: Optional.empty();
	}
}