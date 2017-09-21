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
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.config.GlobalConfig;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.core.service.ConfigService;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.validator.GlobalConfigValidator;

@RequestMapping("mgr/config/global")
@Controller
public class GlobalConfigMgrController extends BaseMgrController {

	@Autowired
	private ConfigService configService;
	@Autowired
	private GlobalConfigValidator globalConfigValidator;

	@InitBinder(value = "globalConfig")
	protected void initBinder(WebDataBinder binder) {
		binder.setValidator(globalConfigValidator);
	}

	@GetMapping("index")
	public String index(Model model) {
		model.addAttribute("config", configService.getGlobalConfig());
		return "mgr/config/global";
	}

	@PostMapping("update")
	@ResponseBody
	public JsonResult update(@Validated @RequestBody GlobalConfig globalConfig) throws LogicException {
		configService.updateGlobalConfig(globalConfig);
		return new JsonResult(true, new Message("global.update.success", "更新成功"));
	}

}
