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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.standard.expression.FragmentExpression;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.NoOpToken;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.standard.processor.StandardDefaultAttributesTagProcessor;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.util.EscapedAttributeUtils;

/**
 * 用来处理动态属性
 * 
 * @author mhlx
 *
 */
abstract class DefaultAttributesTagProcessor extends AbstractElementTagProcessor {

	private static final String DYNAMIC_ATT_PREFIX = "th:";

	public DefaultAttributesTagProcessor(TemplateMode templateMode, String dialectPrefix, String elementName,
			boolean prefixElementName, String attributeName, boolean prefixAttributeName, int precedence) {
		super(templateMode, dialectPrefix, elementName, prefixElementName, attributeName, prefixAttributeName,
				precedence);
	}

	/**
	 * 动态解析将th:开头的属性
	 * 
	 * @param context
	 * @param tag
	 * @return
	 */
	protected Map<String, String> processAttribute(final ITemplateContext context, IProcessableElementTag tag) {
		Map<String, String> attMap = new HashMap<>();
		for (final IAttribute attribute : tag.getAllAttributes()) {
			String completeName = attribute.getAttributeCompleteName();
			if (completeName.startsWith(DYNAMIC_ATT_PREFIX)) {
				processDefaultAttribute(context, tag, attribute, attMap);
			} else {
				attMap.put(completeName, attribute.getValue());
			}
		}

		return attMap;
	}

	/*
	 * copied from StandardDefaultAttributesTagProcessor
	 */
	private void processDefaultAttribute(final ITemplateContext context, final IProcessableElementTag tag,
			final IAttribute attribute, Map<String, String> attMap) {

		try {

			final String attributeValue = EscapedAttributeUtils.unescapeAttribute(context.getTemplateMode(),
					attribute.getValue());

			final String newAttributeName = attribute.getAttributeCompleteName().substring(3);

			if (newAttributeName.trim().isEmpty()) {
				return;
			}

			/*
			 * Obtain the parser
			 */
			final IStandardExpressionParser expressionParser = StandardExpressions
					.getExpressionParser(context.getConfiguration());

			/*
			 * Execute the expression, handling nulls in a way consistent with
			 * the rest of the Standard Dialect
			 */
			final Object expressionResult;
			if (attributeValue != null) {

				final IStandardExpression expression = expressionParser.parseExpression(context, attributeValue);

				if (expression != null) {
					if (expression instanceof FragmentExpression) {
						// This is merely a FragmentExpression (not complex, not
						// combined with anything), so we can apply a shortcut
						// so that we don't require a "null" result for this
						// expression if the template does not exist. That will
						// save a call to resource.exists() which might be
						// costly.

						final FragmentExpression.ExecutedFragmentExpression executedFragmentExpression = FragmentExpression
								.createExecutedFragmentExpression(context, (FragmentExpression) expression,
										StandardExpressionExecutionContext.NORMAL);

						expressionResult = FragmentExpression.resolveExecutedFragmentExpression(context,
								executedFragmentExpression, true);
					} else {
						expressionResult = expression.execute(context);
					}
				} else {
					expressionResult = null;
				}

			} else {
				expressionResult = null;
			}

			/*
			 * If the result of this expression is NO-OP, there is nothing to
			 * execute
			 */
			if (expressionResult == NoOpToken.VALUE) {
				return;
			}

			final String newAttributeValue = Objects.toString(expressionResult,null);
			
			/*
			 * Set the new value, removing the attribute completely if the
			 * expression evaluated to null
			 */
			if (newAttributeValue == null || newAttributeValue.length() == 0) {
				return;
			} else {
				attMap.put(newAttributeName, newAttributeValue);
			}

		} catch (final TemplateProcessingException e) {
			// This is a nice moment to check whether the execution raised an
			// error and, if so, add location information
			// Note this is similar to what is done at the superclass
			// AbstractElementTagProcessor, but we can be more
			// specific because we know exactly what attribute was being
			// executed and caused the error
			if (!e.hasTemplateName()) {
				e.setTemplateName(tag.getTemplateName());
			}
			if (!e.hasLineAndCol()) {
				e.setLineAndCol(attribute.getLine(), attribute.getCol());
			}
			throw e;
		} catch (final Exception e) {
			throw new TemplateProcessingException("Error during execution of processor '"
					+ StandardDefaultAttributesTagProcessor.class.getName() + "'", tag.getTemplateName(),
					attribute.getLine(), attribute.getCol(), e);
		}

	}

}
