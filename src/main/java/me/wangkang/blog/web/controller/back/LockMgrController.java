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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.lock.LockManager;
import me.wangkang.blog.web.JsonResult;

@Controller
@RequestMapping("mgr/lock")
public class LockMgrController extends BaseMgrController {

	@Autowired
	private LockManager lockManager;

	@GetMapping(value = "all")
	@ResponseBody
	public JsonResult allLock() {
		return new JsonResult(true, lockManager.allLock());
	}

}
