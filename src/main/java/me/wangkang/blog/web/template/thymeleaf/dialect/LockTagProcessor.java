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

import org.springframework.context.ApplicationContext;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import me.wangkang.blog.core.lock.LockManager;
import me.wangkang.blog.core.lock.SimpleLockResource;
import me.wangkang.blog.core.security.Environment;

/**
 * {@link http://www.thymeleaf.org/doc/tutorials/3.0/extendingthymeleaf.html#creating-our-own-dialect}
 * 
 * @author mhlx
 *
 */
public class LockTagProcessor extends AbstractElementTagProcessor {

	private static final String TAG_NAME = "lock";
	private static final int PRECEDENCE = 1000;
	private static final String ID = "id";

	public LockTagProcessor(String dialectPrefix, ApplicationContext applicationContext) {
		super(TemplateMode.HTML, // This processor will apply only to HTML mode
				dialectPrefix, // Prefix to be applied to name for matching
				TAG_NAME, // Tag name: match specifically this tag
				false, // Apply dialect prefix to tag name
				null, // No attribute name: will match by tag name
				false, // No prefix to be applied to attribute name
				PRECEDENCE); // Precedence (inside dialect's own precedence)
		this.lockManager = applicationContext.getBean(LockManager.class);
	}

	private final LockManager lockManager;

	@Override
	protected final void doProcess(ITemplateContext context, IProcessableElementTag tag,
			IElementTagStructureHandler structureHandler) {
		try {
			String lockId = tag.getAttributeValue(ID);
			if (lockId != null && !Environment.isLogin()) {
				String resourceId = context.getTemplateData().getTemplate();
				lockManager.openLock(new SimpleLockResource(resourceId, lockId));
			}
		} finally {
			structureHandler.removeElement();
		}
	}
}
