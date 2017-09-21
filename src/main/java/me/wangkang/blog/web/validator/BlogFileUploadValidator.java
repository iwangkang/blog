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

import java.io.File;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.multipart.MultipartFile;

import me.wangkang.blog.core.vo.BlogFileUpload;

@Component
public class BlogFileUploadValidator implements Validator {

	private static final int MAX_FILE_NAME_LENGTH = BlogFileValidator.MAX_FILE_NAME_LENGTH;

	@Override
	public boolean supports(Class<?> clazz) {
		return BlogFileUpload.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		BlogFileUpload upload = (BlogFileUpload) target;
		List<MultipartFile> files = upload.getFiles();
		if (CollectionUtils.isEmpty(files)) {
			errors.reject("file.uploadfiles.blank", "需要上传文件为空");
			return;
		}
		for (MultipartFile file : files) {
			if (file.getOriginalFilename().length() > MAX_FILE_NAME_LENGTH) {
				errors.reject("file.name.toolong", new Object[] { MAX_FILE_NAME_LENGTH },
						"文件名不能超过" + MAX_FILE_NAME_LENGTH + "个字符");
				return;
			}
			try {
				new File(file.getOriginalFilename()).getCanonicalPath();
			} catch (Exception e) {
				errors.reject("file.name.valid", "文件名非法");
				return;
			}
			if (file.isEmpty()) {
				errors.reject("file.content.blank", "文件内容不能为空");
				return;
			}
		}
		if (upload.getStore() == null) {
			errors.reject("file.uploadstore.blank", "文件存储器为空");
			return;
		}
	}

}
