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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.pageparam.SpaceQueryParam;
import me.wangkang.blog.core.service.SpaceService;
import me.wangkang.blog.util.Validators;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.validator.SpaceValidator;

@Controller
@RequestMapping("mgr/space")
public class SpaceMgrController extends BaseMgrController {

	@Autowired
	private SpaceService spaceService;
	@Autowired
	private SpaceValidator spaceValidator;

	@InitBinder(value = "space")
	protected void initBinder(WebDataBinder binder) {
		binder.setValidator(spaceValidator);
	}

	@GetMapping("index")
	public String index(SpaceQueryParam spaceQueryParam, Model model) {
		model.addAttribute("spaces", spaceService.querySpace(spaceQueryParam));
		return "mgr/user/space";
	}

	@PostMapping("add")
	@ResponseBody
	public JsonResult add(@RequestBody @Validated Space space) throws LogicException {
		if (Validators.isEmptyOrNull(space.getLockId(), true)) {
			space.setLockId(null);
		}
		spaceService.addSpace(space);
		return new JsonResult(true, new Message("space.add.success", "新增成功"));
	}

	@PostMapping("update")
	@ResponseBody
	public JsonResult update(@RequestBody @Validated Space space) throws LogicException {
		if (Validators.isEmptyOrNull(space.getLockId(), true)) {
			space.setLockId(null);
		}
		spaceService.updateSpace(space);
		return new JsonResult(true, new Message("space.update.success", "更新成功"));
	}

	@PostMapping("delete/{id}")
	@ResponseBody
	public JsonResult delete(@PathVariable("id") Integer id) throws LogicException {
		spaceService.deleteSpace(id);
		return new JsonResult(true, new Message("space.delete.success", "删除成功"));
	}

	@GetMapping("get/{id}")
	@ResponseBody
	public JsonResult get(@PathVariable("id") Integer id) {
		return spaceService.getSpace(id).map(space -> new JsonResult(true, space))
				.orElse(new JsonResult(false, new Message("space.notExists")));
	}
}
