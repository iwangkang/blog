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
package me.wangkang.blog.core.security.input;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 标签属性
 * 
 * @author Administrator
 *
 */
public class Attribute {
	private String name;
	private String protocols;
	private String enforce;

	public Attribute() {
		super();
	}

	public Attribute(String name) {
		super();
		this.name = name;
	}

	public Attribute(String name, String... protocols) {
		super();
		this.name = name;
		if (protocols != null && protocols.length > 0) {
			this.protocols = Arrays.stream(protocols).collect(Collectors.joining(","));
		}
	}

	public Attribute(String name, String protocols, String enforce) {
		super();
		this.name = name;
		this.protocols = protocols;
		this.enforce = enforce;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getProtocols() {
		return protocols;
	}

	public void setProtocols(String protocols) {
		this.protocols = protocols;
	}

	public String getEnforce() {
		return enforce;
	}

	public void setEnforce(String enforce) {
		this.enforce = enforce;
	}

}