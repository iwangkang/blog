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

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.template.ExportPage;
import me.wangkang.blog.web.template.Fragment;
import me.wangkang.blog.web.template.Page;

@Component
public class ExportPageValidator implements Validator {

	private FragmentValidator fragmentValidator = new FragmentValidator();
	private ThisPageValidator thisPageValidator = new ThisPageValidator();

	@Override
	public boolean supports(Class<?> clazz) {
		return ExportPage.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		ExportPage exportPage = (ExportPage) target;
		Page page = exportPage.getPage();
		if (page == null) {
			errors.reject("page.null", "页面不能为空");
			return;
		}
		thisPageValidator.validate(page, errors);
		if (errors.hasErrors()) {
			return;
		}
		List<Fragment> fragments = exportPage.getFragments();
		if (fragments == null) {
			exportPage.setFragments(new ArrayList<>());
		} else {
			for (Fragment fragment : fragments) {
				fragmentValidator.validate(fragment, errors);
				if (errors.hasErrors()) {
					return;
				}
			}
		}
	}

	private final class ThisPageValidator extends PageValidator {

		@Override
		public void validate(Object target, Errors errors) {
			Page page = (Page) target;
			String name = page.getName();
			if (Validators.isEmptyOrNull(name, true)) {
				errors.reject("page.name.blank", "页面名称不能为空");
				return;
			}
			if (name.length() > PAGE_NAME_MAX_LENGTH) {
				errors.reject("page.name.toolong", new Object[] { PAGE_NAME_MAX_LENGTH },
						"页面名称不能超过" + PAGE_NAME_MAX_LENGTH + "个字符");
				return;
			}
			String pageTpl = page.getTpl();
			if (Validators.isEmptyOrNull(pageTpl, true)) {
				errors.reject("page.tpl.null", "页面模板不能为空");
				return;
			}
			if (pageTpl.length() > PAGE_TPL_MAX_LENGTH) {
				errors.reject("page.tpl.toolong", new Object[] { PAGE_TPL_MAX_LENGTH },
						"页面模板不能超过" + PAGE_TPL_MAX_LENGTH + "个字符");
				return;
			}
			String alias = validateAlias(page.getAlias(), errors);
			if (errors.hasErrors()) {
				return;
			}
			page.setAlias(alias);
		}

	}

}
