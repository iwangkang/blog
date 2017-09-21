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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.exception.LogicException;
import me.wangkang.blog.core.security.Environment;
import me.wangkang.blog.core.vo.DataBind;
import me.wangkang.blog.util.Validators;

public abstract class DataTagProcessor<T> {

	/**
	 * 是否忽略逻辑异常
	 */
	private static final String DATA_NAME = "dataName";

	private String name;// 数据名，唯一
	private String dataName;// 默认数据绑定名，唯一
	private boolean callable;// 是否可以被ajax调用

	protected static final Logger LOGGER = LoggerFactory.getLogger(DataTagProcessor.class);

	/**
	 * 构造器
	 * 
	 * @param name
	 *            数据处理器名称
	 * @param dataName
	 *            页面dataName
	 */
	public DataTagProcessor(String name, String dataName) {
		this.name = name;
		this.dataName = dataName;
	}

	/**
	 * 查询数据
	 * 
	 * @param variables
	 * @param attributes
	 * @return
	 * @throws LogicException
	 */
	public final DataBind getData(Map<String, String> attributes) throws LogicException {
		Attributes atts = new Attributes(attributes);
		T result = query(atts);
		DataBind bind = new DataBind();
		bind.setData(result);
		String dataNameAttV = attributes.get(DATA_NAME);
		if (validDataName(dataNameAttV)) {
			bind.setDataName(dataNameAttV);
		} else {
			bind.setDataName(dataName);
		}
		return bind;
	}

	protected abstract T query(Attributes attributes) throws LogicException;

	public String getName() {
		return name;
	}

	public String getDataName() {
		return dataName;
	}

	public boolean isCallable() {
		return callable;
	}

	public void setCallable(boolean callable) {
		this.callable = callable;
	}

	protected Space getCurrentSpace() {
		return Environment.getSpace();
	}

	private boolean validDataName(String dataName) {
		return Validators.isLetter(dataName);
	}

	public final class Attributes {
		private final Map<String, String> attMap;

		private Attributes(Map<String, String> attMap) {
			this.attMap = attMap == null ? new HashMap<>() : attMap;
		}

		public String getOrDefault(String key, String defaultValue) {
			return attMap.getOrDefault(key, defaultValue);
		}

		public String get(String key) {
			return attMap.get(key);
		}

		/**
		 * 将某个属性转化为Enum
		 * 
		 * @param name
		 *            属性名
		 * @param e
		 *            如果转化失败或者属性不存在，返回null
		 * @return
		 */
		public <E extends Enum<E>> E getEnum(String name, Class<E> e, E defaultValue) {
			String attV = attMap.get(name);
			if (attV == null) {
				return defaultValue;
			}
			try {
				return Enum.valueOf(e, attV);
			} catch (IllegalArgumentException ex) {
			}
			return defaultValue;
		}

		/**
		 * 将某个属性转化为boolean
		 * 
		 * @param name
		 *            属性名
		 * @param defaultValue
		 *            默认值 如果属性不存在，返回默认值
		 * @return 如果属性不存在，返回null
		 */
		public Boolean getBoolean(String name, Boolean defaultValue) {
			String attV = attMap.get(name);
			if (attV == null) {
				return defaultValue;
			}
			return Boolean.parseBoolean(attV);
		}

		/**
		 * 将某个属性转化为Integer
		 * 
		 * @param name
		 *            属性名
		 * @param defaultValue
		 *            如果属性不存在，返回默认值
		 * @return 如果转化失败或者不存在该属性，返回null
		 * 
		 * @see NumberFormatException
		 */
		public Integer getInteger(String name, Integer defaultValue) {
			String attV = attMap.get(name);
			if (attV == null) {
				return defaultValue;
			}
			try {
				return Integer.parseInt(attV);
			} catch (NumberFormatException e) {
			}
			return defaultValue;
		}

		/**
		 * 将某个属性转化为Set
		 * 
		 * @param name
		 *            属性名
		 * @param split
		 *            分割字符
		 * @return 如果属性不存在，返回空Set，不返回null
		 */
		public Set<String> getSet(String name, String split) {
			return new HashSet<>(getList(name, split));
		}

		/**
		 * 将某个属性转化为List
		 * 
		 * @param name
		 *            属性名
		 * @param split
		 *            分割字符
		 * @return 如果属性不存在，返回空Set，不返回null
		 */
		public List<String> getList(String name, String split) {
			String[] array = getArray(name, split);
			if (array == null) {
				return new ArrayList<>();
			}
			List<String> list = new ArrayList<>(array.length);
			for (String str : array) {
				list.add(str);
			}
			return list;
		}

		/**
		 * 将某个属性转化为Array
		 * 
		 * @param name
		 *            属性名
		 * @param split
		 *            分割字符
		 * @return 如果属性不存在，返回null
		 */
		public String[] getArray(String name, String split) {
			String attV = attMap.get(name);
			if (attV != null) {
				return attV.split(split);
			}
			return null;
		}

		@Override
		public String toString() {
			return "Attributes [attMap=" + attMap + "]";
		}
	}
}
