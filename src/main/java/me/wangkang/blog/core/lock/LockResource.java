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

import java.io.Serializable;
import java.util.Optional;

/**
 * 被锁的资源
 * 
 * @author Administrator
 *
 */
public interface LockResource extends Serializable {

	/**
	 * 被锁保护的资源，应该提供一个唯一的ID
	 * 
	 * @return 锁资源id
	 */
	String getResourceId();

	/**
	 * 获取锁ID
	 * 
	 * @return 锁id
	 */
	Optional<String[]> getLockIds();

}
