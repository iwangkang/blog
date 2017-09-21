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
package me.wangkang.blog.core.mybatis.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import me.wangkang.blog.core.entity.Tag;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.util.Validators;

/**
 * 用来将mysql取出的tag 字符串转化为Set&lt;Tag&gt;
 * 
 * @author Administrator
 *
 */
public class TagsTypeHandler extends BaseTypeHandler<Set<Tag>> {
	@Override
	public Set<Tag> getNullableResult(ResultSet rs, String str) throws SQLException {
		return toTags(rs.getString(str));
	}

	@Override
	public Set<Tag> getNullableResult(ResultSet rs, int pos) throws SQLException {
		return toTags(rs.getString(pos));
	}

	@Override
	public void setNonNullParameter(PreparedStatement arg0, int arg1, Set<Tag> arg2, JdbcType arg3)
			throws SQLException {
		throw new SystemException("不支持这个方法");
	}

	protected Set<Tag> toTags(String str) {
		if (Validators.isEmptyOrNull(str, true)) {
			return Collections.emptySet();
		}
		return Arrays.stream(str.split(",")).map(Tag::new).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	@Override
	public Set<Tag> getNullableResult(CallableStatement arg0, int arg1) throws SQLException {
		throw new SystemException("不支持这个方法");
	}

}
