package me.wangkang.blog.web.template.thymeleaf.dialect;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import me.wangkang.blog.web.template.ParseContext;
import me.wangkang.blog.web.template.ParseContextHolder;
import me.wangkang.blog.web.template.TemplateReturnValueHandler;

/**
 * 
 * @see ParseContext
 * @see TemplateReturnValueHandler
 * @author mhlx
 */
public class MediaTypeTagProcessor extends AbstractElementTagProcessor {

	private static final String TAG_NAME = "mediaType";
	private static final int PRECEDENCE = 1000;

	public MediaTypeTagProcessor(String dialectPrefix) {
		super(TemplateMode.HTML, // This processor will apply only to HTML mode
				dialectPrefix, // Prefix to be applied to name for matching
				TAG_NAME, // Tag name: match specifically this tag
				false, // Apply dialect prefix to tag name
				null, // No attribute name: will match by tag name
				false, // No prefix to be applied to attribute name
				PRECEDENCE); // Precedence (inside dialect's own precedence)
	}

	@Override
	protected void doProcess(ITemplateContext context, IProcessableElementTag tag,
			IElementTagStructureHandler structureHandler) {

		try {
			String value = tag.getAttributeValue("value");
			try {
				ParseContextHolder.getContext().setMediaType(MediaType.valueOf(value));
			} catch (InvalidMediaTypeException e) {

			}

		} finally {
			structureHandler.removeElement();
		}

	}

}
