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
package me.wangkang.blog.web.template;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TemplateRenderErrorDescription implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private List<TemplateErrorInfo> templateErrorInfos = new ArrayList<>();
	private String expression;// 表达式

	public TemplateRenderErrorDescription() {
		super();
	}

	public List<TemplateErrorInfo> getTemplateErrorInfos() {
		return templateErrorInfos;
	}

	public void addTemplateErrorInfos(TemplateErrorInfo templateErrorInfo) {
		this.templateErrorInfos.add(templateErrorInfo);
	}

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public static class TemplateErrorInfo implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private final String templateName;
		private final Integer line;
		private final Integer col;

		public TemplateErrorInfo(String templateName, Integer line, Integer col) {
			super();
			this.templateName = templateName;
			this.line = line;
			this.col = col;
		}

		public String getTemplateName() {
			return templateName;
		}

		public Integer getLine() {
			return line;
		}

		public Integer getCol() {
			return col;
		}

	}

}
