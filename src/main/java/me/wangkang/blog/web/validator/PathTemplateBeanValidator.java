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
package me.wangkang.blog.web.validator;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import me.wangkang.blog.core.vo.PathTemplateBean;
import me.wangkang.blog.util.Validators;

@Component
public class PathTemplateBeanValidator implements Validator {

	@Override
	public boolean supports(Class<?> clazz) {
		return PathTemplateBean.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object o, Errors errors) {
		PathTemplateBean bean = (PathTemplateBean) o;
		String tpl = bean.getTpl();

		if (Validators.isEmptyOrNull(tpl, true)) {
			errors.reject("pathTemplate.tpl.null", "模板不能为空");
			return;
		}

		if (tpl.length() > PageValidator.PAGE_TPL_MAX_LENGTH) {
			errors.reject("pathTemplate.tpl.toolong", new Object[] { PageValidator.PAGE_TPL_MAX_LENGTH },
					"模板不能超过" + PageValidator.PAGE_TPL_MAX_LENGTH + "个字符");
			return;
		}

		PageValidator.validateAlias(bean.getPath(), errors);
		if (errors.hasErrors()) {
			return;
		}

		if (bean.isPub() && bean.isRegistrable()) {
			errors.reject("pathTemplate.type.ambiguous", "无法确定模板类型");
		}
	}

}
