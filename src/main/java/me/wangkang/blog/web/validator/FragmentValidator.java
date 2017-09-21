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

import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.template.Fragment;

@Component
public class FragmentValidator implements Validator {

	private static final int MAX_NAME_LENGTH = 20;
	public static final int MAX_TPL_LENGTH = 20000;
	private static final int MAX_DESCRIPTION_LENGTH = 500;

	public static final String NAME_PATTERN = "^[A-Za-z0-9\u4E00-\u9FA5_-]+$";

	@Override
	public boolean supports(Class<?> clazz) {
		return Fragment.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		Fragment fragment = (Fragment) target;
		String name = fragment.getName();
		if (Validators.isEmptyOrNull(name, true)) {
			errors.reject("fragment.user.name.blank", "模板片段名为空");
			return;
		}
		if (name.length() > MAX_NAME_LENGTH) {
			errors.reject("fragment.user.name.toolong", new Object[] { MAX_NAME_LENGTH },
					"模板片段名长度不能超过" + MAX_NAME_LENGTH + "个字符");
			return;
		}
		if (!name.matches(NAME_PATTERN)) {
			errors.reject("fragment.user.name.invalid", "模板片段名称校验失败");
			return;
		}
		String tpl = fragment.getTpl();
		if (Validators.isEmptyOrNull(tpl, true)) {
			errors.reject("fragment.user.tpl.null", "模板片段模板不能为空");
			return;
		}
		if (tpl != null && tpl.length() > MAX_TPL_LENGTH) {
			errors.reject("fragment.user.tpl.toolong", new Object[] { MAX_TPL_LENGTH },
					"模板片段模板长度不能超过" + MAX_TPL_LENGTH + "个字符");
			return;
		}
		String description = fragment.getDescription();
		if (description == null) {
			errors.reject("fragment.user.description.null", "模板片段描述不能为空");
			return;
		}
		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			errors.reject("fragment.user.description.toolong", new Object[] { MAX_DESCRIPTION_LENGTH },
					"模板片段描述长度不能超过" + MAX_DESCRIPTION_LENGTH + "个字符");
			return;
		}
	}

}
