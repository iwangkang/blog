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
package me.wangkang.blog.core.exception;

import me.wangkang.blog.core.message.Message;

/**
 * 业务异常，这个异常不做任何的日志
 * 
 * @author Administrator
 *
 */
public class RuntimeLogicException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final LogicException logicException;

	/**
	 * @param message
	 *            异常信息
	 */
	public RuntimeLogicException(LogicException logicException) {
		this.logicException = logicException;
	}

	public RuntimeLogicException(Message message) {
		this.logicException = new LogicException(message);
	}

	public LogicException getLogicException() {
		return logicException;
	}

	@Override
	public Throwable fillInStackTrace() {
		return this;
	}
}
