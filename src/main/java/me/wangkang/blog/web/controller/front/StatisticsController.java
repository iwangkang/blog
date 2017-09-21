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
package me.wangkang.blog.web.controller.front;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.pageparam.SpaceQueryParam;
import me.wangkang.blog.core.service.SpaceService;
import me.wangkang.blog.core.service.StatisticsService;

@Controller
public class StatisticsController {

	@Autowired
	private StatisticsService statisticsService;
	@Autowired
	private SpaceService spaceService;

	@GetMapping("mgr/statistics")
	public String queryStatisticsDetail(@RequestParam(value = "spaceId", required = false) Integer spaceId,
			ModelMap model, RedirectAttributes ra) {
		model.addAttribute("spaces", spaceService.querySpace(new SpaceQueryParam()));
		try {
			model.addAttribute("statistics", statisticsService.queryStatisticsDetail(spaceId));
			return "mgr/statistics/index";
		} catch (LogicException e) {
			ra.addFlashAttribute(Constants.ERROR, e.getLogicMessage());
			return "redirect:/mgr/statistics";
		}
	}

}
