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
package me.wangkang.blog.web.controller.back;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.entity.BlogFile;
import me.wangkang.blog.core.entity.BlogFile.BlogFileType;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.file.FileStore;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.pageparam.BlogFileQueryParam;
import me.wangkang.blog.core.service.FileService;
import me.wangkang.blog.core.vo.BlogFileUpload;
import me.wangkang.blog.core.vo.FileStoreBean;
import me.wangkang.blog.core.vo.UploadedFile;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.validator.BlogFileQueryParamValidator;
import me.wangkang.blog.web.validator.BlogFileUploadValidator;
import me.wangkang.blog.web.validator.BlogFileValidator;

@Controller
@RequestMapping("mgr/file")
public class FileMgrController extends BaseMgrController {

	@Autowired
	private FileService fileService;
	@Autowired
	private BlogFileQueryParamValidator blogFileParamValidator;
	@Autowired
	private BlogFileUploadValidator blogFileUploadValidator;
	@Autowired
	private BlogFileValidator blogFileValidator;

	@InitBinder(value = "blogFileQueryParam")
	protected void initBlogFileQueryParamBinder(WebDataBinder binder) {
		binder.setValidator(blogFileParamValidator);
	}

	@InitBinder(value = "blogFileUpload")
	protected void initBlogUploadBinder(WebDataBinder binder) {
		binder.setValidator(blogFileUploadValidator);
	}

	@InitBinder(value = "blogFile")
	protected void initBlogFileBinder(WebDataBinder binder) {
		binder.setValidator(blogFileValidator);
	}

	@GetMapping("index")
	public String index(@Validated BlogFileQueryParam blogFileQueryParam, Model model) {
		try {
			model.addAttribute("result", fileService.queryBlogFiles(blogFileQueryParam));
			model.addAttribute("stores", fileService.allStorableStores());
		} catch (LogicException e) {
			model.addAttribute(Constants.ERROR, e.getLogicMessage());
		}
		return "mgr/file/index";
	}

	@GetMapping("stores")
	@ResponseBody
	public List<FileStoreBean> allServers() {
		List<FileStore> stores = fileService.allStorableStores();
		return stores.stream().map(FileStoreBean::new).collect(Collectors.toList());
	}

	@GetMapping("query")
	@ResponseBody
	public JsonResult query(@Validated BlogFileQueryParam blogFileQueryParam) throws LogicException {
		blogFileQueryParam.setQuerySubDir(false);
		blogFileQueryParam.setExtensions(new HashSet<>());
		return new JsonResult(true, fileService.queryBlogFiles(blogFileQueryParam));
	}

	@PostMapping("upload")
	@ResponseBody
	public JsonResult upload(@Validated BlogFileUpload blogFileUpload, BindingResult result) throws LogicException {
		if (result.hasErrors()) {
			List<ObjectError> errors = result.getAllErrors();
			for (ObjectError error : errors) {
				return new JsonResult(false,
						new Message(error.getCode(), error.getDefaultMessage(), error.getArguments()));
			}
		}
		List<UploadedFile> uploadedFiles = fileService.upload(blogFileUpload);
		return new JsonResult(true, uploadedFiles);
	}

	@PostMapping("delete")
	@ResponseBody
	public JsonResult delete(@RequestParam("id") Integer id) throws LogicException {
		fileService.delete(id);
		return new JsonResult(true, new Message("file.delete.success", "删除成功"));
	}

	@GetMapping("{id}/pro")
	@ResponseBody
	public JsonResult pro(@PathVariable("id") int id) throws LogicException {
		return new JsonResult(true, fileService.getBlogFileProperty(id));
	}

	@PostMapping("createFolder")
	@ResponseBody
	public JsonResult createFolder(@RequestBody @Validated BlogFile blogFile) throws LogicException {
		blogFile.setCf(null);
		blogFile.setType(BlogFileType.DIRECTORY);
		fileService.createFolder(blogFile);
		return new JsonResult(true, new Message("file.create.success", "创建成功"));
	}

	@PostMapping("copy")
	@ResponseBody
	public JsonResult copy(@RequestParam("sourceId") Integer sourceId, @RequestParam("folderPath") String folderPath)
			throws LogicException {
		JsonResult validResult = validPath("folderPath", folderPath);
		if (validResult != null) {
			return validResult;
		}
		fileService.copy(sourceId, folderPath);
		return new JsonResult(true, new Message("file.copy.success", "拷贝成功"));
	}

	@PostMapping("move")
	@ResponseBody
	public JsonResult move(@RequestParam("sourceId") Integer sourceId, @RequestParam("destPath") String destPath)
			throws LogicException {
		JsonResult validResult = validPath("destPath", destPath);
		if (validResult != null) {
			return validResult;
		}
		fileService.move(sourceId, destPath);
		return new JsonResult(true, new Message("file.move.success", "移动成功"));
	}

	@PostMapping("rename")
	@ResponseBody
	public JsonResult rename(@RequestParam("sourceId") Integer sourceId, @RequestParam("newName") String newName)
			throws LogicException {
		if (Validators.isEmptyOrNull(newName, true)) {
			return new JsonResult(false, new Message("file.name.blank", "文件名不能为空"));
		}
		if (!BlogFileValidator.checkPath(newName)) {
			return new JsonResult(false,
					new Message("file.fileName.valid", "文件名" + newName + "无效，文件名必须为字母数字或者汉字或者_和-", newName));
		}
		fileService.rename(sourceId, newName);
		return new JsonResult(true, new Message("file.move.success", "移动成功"));
	}

	private JsonResult validPath(String objectName, String path) {
		Errors bingdingResult = new MapBindingResult(new HashMap<>(), objectName);
		BlogFileValidator.validFilePath(path, bingdingResult);
		if (bingdingResult.hasErrors()) {
			List<ObjectError> errors = bingdingResult.getAllErrors();
			for (ObjectError error : errors) {
				return new JsonResult(false,
						new Message(error.getCode(), error.getDefaultMessage(), error.getArguments()));
			}
		}
		return null;
	}
}
