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
package me.wangkang.blog.web.template.thymeleaf;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.thymeleaf.dialect.IPreProcessorDialect;
import org.thymeleaf.preprocessor.IPreProcessor;
import org.thymeleaf.preprocessor.PreProcessor;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;

import me.wangkang.blog.web.template.thymeleaf.dialect.PreTemplateHandler;
import me.wangkang.blog.web.template.thymeleaf.dialect.TemplateDialect;
import me.wangkang.blog.web.template.thymeleaf.dialect.TransactionDialect;

public class ThymeleafTemplateEngine extends SpringTemplateEngine implements InitializingBean {

	@Autowired
	private ApplicationContext applicationContext;

	public ThymeleafTemplateEngine() {
		super();
		addDialect(new IPreProcessorDialect() {

			@Override
			public String getName() {
				return "Blog Template Engine PreProcessor Dialect";
			}

			@Override
			public Set<IPreProcessor> getPreProcessors() {
				return new HashSet<>(
						Arrays.asList(new PreProcessor(TemplateMode.HTML, PreTemplateHandler.class, 1000)));
			}

			@Override
			public int getDialectPreProcessorPrecedence() {
				return 1000;
			}
		});
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		addDialect(new TemplateDialect(applicationContext));
		addDialect(new TransactionDialect(applicationContext));
	}

}