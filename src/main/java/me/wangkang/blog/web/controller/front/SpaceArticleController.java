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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.service.ArticleService;
import me.wangkang.blog.util.UrlUtils;
import me.wangkang.blog.web.JsonResult;

@Controller
@RequestMapping("space/{alias}/article")
public class SpaceArticleController {

	@Autowired
	private ArticleService articleService;

	@PostMapping("hit/{id}")
	@ResponseBody
	public JsonResult hit(@PathVariable("id") Integer id, @RequestHeader("referer") String referer) {
		try {
			UriComponents uc = UriComponentsBuilder.fromHttpUrl(referer).build();
			if (!UrlUtils.match("/space/" + Environment.getSpaceAlias() + "/article/*", uc.getPath())
					&& !UrlUtils.match("/article/*", uc.getPath())) {
				return new JsonResult(false);
			}
		} catch (Exception e) {
			return new JsonResult(false);
		}

		articleService.hit(id);
		return new JsonResult(true);
	}

}
