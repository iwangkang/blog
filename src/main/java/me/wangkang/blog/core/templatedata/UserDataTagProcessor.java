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
package me.wangkang.blog.core.templatedata;

import org.springframework.beans.factory.annotation.Autowired;

import me.wangkang.blog.core.entity.User;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.service.UserQueryService;

public class UserDataTagProcessor extends DataTagProcessor<User> {

	@Autowired
	private UserQueryService userQueryService;

	public UserDataTagProcessor(String name, String dataName) {
		super(name, dataName);
	}

	@Override
	protected User query(Attributes attributes) throws LogicException {
		User user = userQueryService.getUser();
		user.setPassword(null);
		return user;
	}

}
