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
package me.wangkang.blog.core.lock;

import java.util.Optional;

public class SimpleLockResource implements LockResource {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final String resourceId;
	private final String lockId;

	public SimpleLockResource(String resourceId, String lockId) {
		super();
		this.resourceId = resourceId;
		this.lockId = lockId;
	}

	@Override
	public String getResourceId() {
		return resourceId;
	}

	@Override
	public Optional<String[]> getLockIds() {
		return lockId == null ? Optional.empty() : Optional.of(new String[] { lockId });
	}

}
