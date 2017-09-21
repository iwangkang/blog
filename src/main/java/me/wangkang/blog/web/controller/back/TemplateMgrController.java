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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.TemplateEngine;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.pageparam.SpaceQueryParam;
import me.wangkang.blog.core.service.SpaceService;
import me.wangkang.blog.core.vo.ImportRecord;
import me.wangkang.blog.util.Jsons;
import me.wangkang.blog.util.Times;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.template.ExportPage;
import me.wangkang.blog.web.template.Page;
import me.wangkang.blog.web.template.TemplateService;
import me.wangkang.blog.web.validator.ExportPageValidator;

@Controller
@RequestMapping("mgr/template")
public class TemplateMgrController extends BaseMgrController {

	@Autowired
	private TemplateService templateService;
	@Autowired
	private SpaceService spaceService;
	@Autowired
	private TemplateEngine templateEngine;
	@Autowired
	private ExportPageValidator exportPageValidator;

	@PostMapping("export")
	public Object export(@RequestParam(value = "spaceId", required = false) Integer spaceId, RedirectAttributes ra) {
		try {
			List<ExportPage> pages = templateService.exportPage(spaceId);
			return download(pages, spaceId == null ? null
					: spaceService.getSpace(spaceId).orElseThrow(() -> new SystemException("空间" + spaceId + "不存在")));
		} catch (LogicException e) {
			ra.addFlashAttribute(Constants.ERROR, e.getLogicMessage());
			return "redirect:/mgr/template/export";
		}
	}

	@GetMapping("export")
	public String export(ModelMap model) {
		model.addAttribute("spaces", spaceService.querySpace(new SpaceQueryParam()));
		return "mgr/template/export";
	}

	@PostMapping("import")
	@ResponseBody
	public JsonResult importPage(@RequestParam("json") String json,
			@RequestParam(value = "spaceId", required = false) Integer spaceId) {
		List<ImportRecord> records = new ArrayList<>();
		List<ExportPage> exportPages = new ArrayList<>();
		try {
			exportPages = Jsons.readList(ExportPage[].class, json);
		} catch (Exception e) {
			records.add(new ImportRecord(false, new Message("tpl.parse.fail", "模板解析失败")));
			return new JsonResult(true, records);
		}
		List<ExportPage> toImportPages = new ArrayList<>();
		MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "exportPage");
		// validate
		for (ExportPage exportPage : exportPages) {
			Page page = exportPage.getPage();
			if (page == null) {
				continue;
			}
			exportPageValidator.validate(exportPage, bindingResult);
			if (bindingResult.hasErrors()) {

				List<ObjectError> errors = bindingResult.getAllErrors();
				for (ObjectError error : errors) {
					records.add(new ImportRecord(false,
							new Message(error.getCode(), error.getDefaultMessage(), error.getArguments())));
					break;
				}

				return new JsonResult(true, records);
			}
			toImportPages.add(exportPage);
			bindingResult.getTargetMap().clear();
		}
		records.addAll(templateService.importPage(spaceId, toImportPages));
		return new JsonResult(true, records);
	}

	@PostMapping("clearCache")
	@ResponseBody
	public JsonResult clearPageCache() {
		templateEngine.getConfiguration().getTemplateManager().clearCaches();
		return new JsonResult(true, new Message("clearPageCache.success", "清除缓存成功"));
	}

	@PostMapping("clearPreview")
	@ResponseBody
	public JsonResult clearPreview() {
		templateService.clearPreview();
		return new JsonResult(true, new Message("clearPreview.success", "清除预览页面成功"));
	}

	@GetMapping("other")
	public String other() {
		return "mgr/template/other";
	}

	@GetMapping("dataTags")
	@ResponseBody
	public JsonResult queryDatas() {
		return new JsonResult(true, templateService.queryDataTags());
	}

	private ResponseEntity<byte[]> download(List<ExportPage> pages, Space space) {
		HttpHeaders header = new HttpHeaders();
		header.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		String filenamePrefix = "";
		if (space != null) {
			filenamePrefix += space.getAlias() + "-";
		}
		filenamePrefix += Times.format(Times.now(), "yyyyMMddHHmmss");
		header.set("Content-Disposition", "attachment; filename=" + filenamePrefix + ".json");
		return new ResponseEntity<>(Jsons.write(pages).getBytes(Constants.CHARSET), header, HttpStatus.OK);
	}
}
