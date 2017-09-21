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
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.entity.Tag;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.pageparam.TagQueryParam;
import me.wangkang.blog.core.service.TagService;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.validator.TagQueryParamValidator;
import me.wangkang.blog.web.validator.TagValidator;

@RequestMapping("mgr/tag")
@Controller
public class TagMgrController extends BaseMgrController {

	@Autowired
	private TagService tagService;
	@Autowired
	private TagValidator tagValidator;
	@Autowired
	private TagQueryParamValidator tagQueryParamValidator;

	@InitBinder(value = "tag")
	protected void initBinder(WebDataBinder binder) {
		binder.setValidator(tagValidator);
	}

	@InitBinder(value = "tagQueryParam")
	protected void initTagQueryParamBinder(WebDataBinder binder) {
		binder.setValidator(tagQueryParamValidator);
	}

	@GetMapping("index")
	public String index(@Validated TagQueryParam tagQueryParam, Model model) {
		model.addAttribute("page", tagService.queryTag(tagQueryParam));
		return "mgr/tag/index";
	}

	@PostMapping("delete")
	@ResponseBody
	public JsonResult delete(@RequestParam("id") Integer id) throws LogicException {
		tagService.deleteTag(id);
		return new JsonResult(true, new Message("tag.delete.success", "删除成功"));
	}

	@PostMapping("update")
	@ResponseBody
	public JsonResult update(@RequestBody @Validated Tag tag,
			@RequestParam(defaultValue = "false", required = false) boolean merge) throws LogicException {
		tagService.updateTag(tag, merge);
		return new JsonResult(true, new Message("tag.update.success", "更新成功"));
	}

}
