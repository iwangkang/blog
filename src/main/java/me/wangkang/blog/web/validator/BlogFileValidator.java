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

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import me.wangkang.blog.core.entity.BlogFile;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.Validators;

@Component
public class BlogFileValidator implements Validator {

	public static final int MAX_PATH_LENGTH = 30;
	public static final int MAX_FILE_NAME_LENGTH = 225;

	private static final String PATH_PATTERN = "^[A-Za-z0-9_-]+$";

	@Override
	public boolean supports(Class<?> clazz) {
		return BlogFile.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		BlogFile file = (BlogFile) target;
		if (file.isDir()) {
			String path = file.getPath();
			validPath(path, errors);
			if (errors.hasErrors()) {
				return;
			}
		}
	}

	/**
	 * valid /a/b/c like
	 * 
	 * @param folderPath
	 * @return
	 */
	public static void validFolderPath(String folderPath, Errors errors) {
		if (folderPath == null) {
			errors.reject("file.path.blank", "路径不能为空");
			return;
		}
		String clean = FileUtils.cleanPath(folderPath);
		if (clean.indexOf('/') != -1) {
			validFolderPath(folderPath.split("/"), errors);
		}
	}

	/**
	 * @param path
	 * @param errors
	 */
	public static void validFilePath(String filePath, Errors errors) {
		if (Validators.isEmptyOrNull(filePath, true)) {
			errors.reject("file.path.blank", "路径不能为空");
			return;
		}
		String path = FileUtils.cleanPath(filePath);
		String fileName;
		if (path.indexOf('/') == -1) {
			fileName = path;
		} else {
			String[] toCopy = path.split("/");
			String[] pathArray = new String[toCopy.length - 1];
			System.arraycopy(toCopy, 0, pathArray, 0, toCopy.length - 1);

			validFolderPath(pathArray, errors);
			if (errors.hasErrors()) {
				return;
			}

			fileName = toCopy[toCopy.length - 1];
		}

		if (Validators.isEmptyOrNull(fileName, true)) {
			errors.reject("file.name.blank", "文件名不能为空");
			return;
		}
		if (fileName.length() > MAX_FILE_NAME_LENGTH) {
			errors.reject("file.name.toolong", new Object[] { fileName, MAX_FILE_NAME_LENGTH },
					"文件名不能超过" + MAX_FILE_NAME_LENGTH + "个字符");
			return;
		}

		if (!checkPath(fileName)) {
			errors.reject("file.fileName.valid", new Object[] { fileName },
					"文件名" + fileName + "无效，文件名必须为字母数字或者汉字或者_和-");
			return;
		}
	}

	private static void validFolderPath(String[] pathArray, Errors errors) {
		for (String _path : pathArray) {
			validPath(_path, errors);
			if (errors.hasErrors()) {
				break;
			}
		}
	}

	public static void validPath(String path, Errors errors) {
		if (Validators.isEmptyOrNull(path, true)) {
			errors.reject("file.path.blank", "路径不能为空");
			return;
		}
		if (path.length() > MAX_PATH_LENGTH) {
			errors.reject("file.path.toolong", new Object[] { path, MAX_PATH_LENGTH },
					"路径" + path + "不能超过" + MAX_PATH_LENGTH + "个字符");
			return;
		}
		if (!checkPath(path)) {
			errors.reject("file.path.valid", new Object[] { path }, "路径" + path + "无效，路径必须为字母数字或者_和-");
			return;
		}
	}

	public static boolean checkPath(String path) {
		if (path.matches(PATH_PATTERN)) {
			try {
				new File(path).getCanonicalPath();
				return true;
			} catch (Exception e) {
				return false;
			}
		}
		return false;
	}
}
