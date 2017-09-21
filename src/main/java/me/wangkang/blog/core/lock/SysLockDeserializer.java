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

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import me.wangkang.blog.core.lock.SysLock.SysLockType;
import me.wangkang.blog.util.Jsons;

public class SysLockDeserializer implements JsonDeserializer<SysLock> {

	@Override
	public SysLock deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
		if (json.isJsonObject()) {
			JsonObject obj = json.getAsJsonObject();
			JsonElement typeEle = obj.get("type");
			SysLockType type = SysLockType.valueOf(typeEle.getAsString());
			switch (type) {
			case PASSWORD:
				return Jsons.readValue(PasswordLock.class, json);
			case QA:
				return Jsons.readValue(QALock.class, json);
			default:
				return null;
			}
		}
		return null;
	}

}
