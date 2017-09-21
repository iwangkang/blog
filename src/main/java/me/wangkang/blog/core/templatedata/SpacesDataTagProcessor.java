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

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.pageparam.SpaceQueryParam;
import me.wangkang.blog.core.service.SpaceService;

/**
 * 查询所有的空间
 * 
 * @author mhlx
 *
 */
public class SpacesDataTagProcessor extends DataTagProcessor<List<Space>> {

	@Autowired
	private SpaceService spaceService;

	public SpacesDataTagProcessor(String name, String dataName) {
		super(name, dataName);
	}

	@Override
	protected List<Space> query(Attributes attributes) throws LogicException {
		return spaceService.querySpace(new SpaceQueryParam());
	}

}
