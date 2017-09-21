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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.vo.DataTag;
import me.wangkang.blog.web.JsonResult;
import me.wangkang.blog.web.Webs;
import me.wangkang.blog.web.template.Fragment;
import me.wangkang.blog.web.template.ParseConfig;
import me.wangkang.blog.web.template.ReadOnlyResponse;
import me.wangkang.blog.web.template.RenderResult;
import me.wangkang.blog.web.template.TemplateRender;
import me.wangkang.blog.web.template.TemplateRenderException;
import me.wangkang.blog.web.template.TemplateService;

@Controller
public class OtherController {

	@Autowired
	private TemplateRender templateRender;
	@Autowired
	private TemplateService templateService;

	@GetMapping({ "data/{tagName}", "space/{alias}/data/{tagName}" })
	@ResponseBody
	public JsonResult queryData(@PathVariable("tagName") String tagName,
			@RequestParam Map<String, String> allRequestParams, HttpServletRequest request,
			HttpServletResponse response) throws LogicException {
		Map<String, String> attMap = new HashMap<>();
		for (Map.Entry<String, String> it : allRequestParams.entrySet()) {
			attMap.put(it.getKey(), it.getValue());
		}
		DataTag tag = new DataTag(Webs.decode(tagName), attMap);
		return templateService.queryData(tag, true).map(bind -> new JsonResult(true, bind))
				.orElse(new JsonResult(false));
	}

	@GetMapping({ "fragment/{fragment}", "space/{alias}/fragment/{fragment}" })
	public void queryFragment(@PathVariable("fragment") String fragment,
			@RequestParam Map<String, String> allRequestParams, HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		try {

			RenderResult result = templateRender.render(
					Fragment.getTemplateName(Webs.decode(fragment), Environment.getSpace()), null, request, new ReadOnlyResponse(response),
					new ParseConfig(true));

			String content = result.getContent();
			MediaType type = result.getType();
			if (type == null) {
				type = MediaType.TEXT_HTML;
			}

			write(content, type, response);

		} catch (TemplateRenderException e) {
			Webs.writeInfo(response, new JsonResult(true, e.getRenderErrorDescription()));
		}
	}

	protected void write(String content, MediaType type, HttpServletResponse response) throws IOException {
		if (MediaType.TEXT_HTML.equals(type)) {
			Webs.writeInfo(response, new JsonResult(true, content));
		} else {
			response.setContentType(type.toString());
			response.setCharacterEncoding(Constants.CHARSET.name());
			response.getWriter().write(content);
			response.getWriter().flush();
		}
	}
}
