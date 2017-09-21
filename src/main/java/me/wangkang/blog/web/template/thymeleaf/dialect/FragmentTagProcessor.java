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
package me.wangkang.blog.web.template.thymeleaf.dialect;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.util.FastStringWriter;

import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.web.template.Fragment;
import me.wangkang.blog.web.template.thymeleaf.UIStackoverflowError;

/**
 * {@link http://www.thymeleaf.org/doc/tutorials/3.0/extendingthymeleaf.html#creating-our-own-dialect}
 * 
 * @author mhlx
 *
 */
public class FragmentTagProcessor extends DefaultAttributesTagProcessor {

	private static final Logger logger = LoggerFactory.getLogger(FragmentTagProcessor.class);

	private static final String TAG_NAME = "fragment";
	private static final int PRECEDENCE = 1000;
	private static final String NAME = "name";

	public FragmentTagProcessor(String dialectPrefix) {
		super(TemplateMode.HTML, // This processor will apply only to HTML mode
				dialectPrefix, // Prefix to be applied to name for matching
				TAG_NAME, // Tag name: match specifically this tag
				false, // Apply dialect prefix to tag name
				null, // No attribute name: will match by tag name
				false, // No prefix to be applied to attribute name
				PRECEDENCE); // Precedence (inside dialect's own precedence)
	}

	@Override
	protected final void doProcess(ITemplateContext context, IProcessableElementTag tag,
			IElementTagStructureHandler structureHandler) {
		Map<String, String> attMap = processAttribute(context, tag);
		String name = attMap.get(NAME);
		if (name != null) {
			String templateName = Fragment.getTemplateName(name, Environment.getSpace());
			try (Writer writer = new FastStringWriter(200)) {

				context.getConfiguration().getTemplateManager().parseAndProcess(
						new TemplateSpec(templateName, null, TemplateMode.HTML, null), context, writer);
				structureHandler.replaceWith(writer.toString(), false);
				return;
			} catch (StackOverflowError e) {
				if (tag.hasLocation()) {
					throw new UIStackoverflowError(templateName, tag.getCol(), tag.getLine(), e);
				} else {
					throw new UIStackoverflowError(templateName, null, null, e);
				}
			} catch (IOException e) {
				// ??
				logger.debug(e.getMessage(), e);
			}
		}
		structureHandler.removeElement();
	}

}
