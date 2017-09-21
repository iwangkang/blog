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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.lock.RequestLock;
import me.wangkang.blog.core.lock.SysLock;
import me.wangkang.blog.core.lock.SysLockProvider;
import me.wangkang.blog.core.message.Message;
import me.wangkang.blog.web.JsonResult;

@Controller
@RequestMapping("mgr/lock/sys")
public class SysLockMgrController extends BaseMgrController {

	@Autowired
	private SysLockProvider sysLockProvider;

	@GetMapping("get/{id}")
	@ResponseBody
	public JsonResult lock(@PathVariable("id") String id) {
		return sysLockProvider.findLock(id).map(sysLock -> new JsonResult(true, sysLock)).orElse(new JsonResult(false));
	}

	@PostMapping("add")
	@ResponseBody
	public JsonResult addLock(@RequestLock SysLock lock) throws LogicException {
		sysLockProvider.addLock(lock);
		return new JsonResult(true, new Message("lock.add.success", "添加成功"));
	}

	@PostMapping("update")
	@ResponseBody
	public JsonResult updateLock(@RequestLock SysLock lock) throws LogicException {
		sysLockProvider.updateLock(lock);
		return new JsonResult(true, new Message("lock.update.success", "更新成功"));
	}

	@PostMapping("delete")
	@ResponseBody
	public JsonResult deleteLock(@RequestParam("id") String id) throws LogicException {
		sysLockProvider.removeLock(id);
		return new JsonResult(true, new Message("lock.delete.success", "删除成功"));
	}

	@GetMapping("index")
	public String index(Model model) {
		model.addAttribute("locks", sysLockProvider.allLock());
		return "mgr/lock/index";
	}

}
