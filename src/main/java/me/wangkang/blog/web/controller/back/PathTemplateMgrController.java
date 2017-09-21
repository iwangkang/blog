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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.vo.PathTemplateBean;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.template.PathTemplate;
import me.wangkang.blog.web.template.TemplateService;
import me.wangkang.blog.web.template.TemplateService.PathTemplateService;
import me.wangkang.blog.web.validator.PathTemplateBeanValidator;

@Controller
@RequestMapping("mgr/template/path")
public class PathTemplateMgrController extends BaseMgrController {
	@Autowired
	private TemplateService templateService;
	@Autowired
	private UrlHelper urlHelper;
	@Autowired
	private PathTemplateBeanValidator pathTemplateBeanValidator;

	@InitBinder(value = "pathTemplateBean")
	protected void initBinder(WebDataBinder binder) {
		binder.setValidator(pathTemplateBeanValidator);
	}

	@GetMapping("index")
	public String index(ModelMap model, @RequestParam(value = "str", required = false) String str) {
		try {
			model.addAttribute("templates", templateService.getPathTemplateService().queryPathTemplates(str));
		} catch (LogicException e) {
			model.addAttribute(Constants.ERROR, e.getLogicMessage());
		}
		return "mgr/template/path/index";
	}

	@GetMapping("reload")
	@ResponseBody
	public JsonResult reload(@RequestParam("path") String path) throws LogicException {
		return new JsonResult(true, templateService.getPathTemplateService().loadPathTemplateFile(path));
	}

	@GetMapping("build")
	public String build(@RequestParam(value = "path", required = false) String path, ModelMap model)
			throws LogicException {
		PathTemplateService pathTemplateService = templateService.getPathTemplateService();
		PathTemplateBean tpl = path == null ? new PathTemplateBean()
				: pathTemplateService.getPathTemplate(PathTemplate.getTemplateName(path)).map(PathTemplateBean::new)
						.orElse(new PathTemplateBean(path));

		model.addAttribute("pathTemplate", tpl);
		return "mgr/template/path/build";
	}

	@PostMapping("build")
	@ResponseBody
	public JsonResult build(@RequestBody @Validated PathTemplateBean pathTemplateBean) throws LogicException {
		PathTemplateService pathTemplateService = templateService.getPathTemplateService();
		pathTemplateService.build(pathTemplateBean);
		return new JsonResult(true);
	}

	@PostMapping("delete")
	@ResponseBody
	public JsonResult delete(@RequestParam(value = "path", required = false, defaultValue = "") String path)
			throws LogicException {
		PathTemplateService pathTemplateService = templateService.getPathTemplateService();
		pathTemplateService.deletePathTemplate(path);
		return new JsonResult(true);
	}

	@PostMapping("preview")
	@ResponseBody
	public JsonResult preview(@RequestBody @Validated PathTemplateBean pathTemplateBean) throws LogicException {
		// 设置空间
		PathTemplate preview = templateService.getPathTemplateService().registerPreview(pathTemplateBean);
		return new JsonResult(true, new PreviewUrl(urlHelper.getUrl() + "/" + preview.getRelativePath()));
	}
}
