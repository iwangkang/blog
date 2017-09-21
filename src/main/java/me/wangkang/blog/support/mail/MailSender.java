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
package me.wangkang.blog.support.mail;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.service.UserQueryService;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.SerializationUtils;
import me.wangkang.blog.util.Validators;

/**
 * 邮件发送服务
 * 
 * @author Administrator
 *
 */
public class MailSender implements InitializingBean, ApplicationListener<ContextClosedEvent> {

	@Autowired
	private JavaMailSender javaMailSender;
	@Autowired
	private UserQueryService userQueryService;

	private ConcurrentLinkedQueue<MessageBean> queue = new ConcurrentLinkedQueue<>();

	private static final Logger LOGGER = LoggerFactory.getLogger(MailSender.class);

	/**
	 * 应用关闭时未发送的信息存入文件中
	 */
	private final Path sdfile = Constants.DAT_DIR.resolve("message_shutdown.dat");

	/**
	 * 将邮件加入发送队列
	 * 
	 * @param mb
	 *            邮件对象
	 */
	public void send(MessageBean mb) {
		queue.add(mb);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (FileUtils.exists(sdfile)) {
			LOGGER.debug("发现序列化文件，执行反序列化操作");

			try {
				queue = SerializationUtils.deserialize(sdfile);
			} catch (Exception e) {
				LOGGER.warn("载入文件：" + sdfile + "失败:" + e.getMessage(), e);
			} finally {
				if (!FileUtils.deleteQuietly(sdfile)) {
					LOGGER.warn("删除序列文件失败");
				}
			}
		}
	}

	/**
	 * 发送队列中的第一封邮件
	 * <p>
	 * <b>仅供定时任务调用</b>
	 * </p>
	 */
	public void sendMailFromQueue() {
		MessageBean mb = queue.poll();
		if (mb != null) {
			sendMail(mb);
		}
	}

	private void sendMail(final MessageBean mb) {
		final String email = Validators.isEmptyOrNull(mb.to, true) ? userQueryService.getUser().getEmail() : mb.to;
		if (Validators.isEmptyOrNull(email, true)) {
			LOGGER.error("接受人邮箱为空，无法发送邮件");
			return;
		}
		try {
			javaMailSender.send(mimeMessage -> {
				MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, mb.html, Constants.CHARSET.name());
				helper.setText(mb.text, mb.html);
				helper.setTo(email);
				helper.setSubject(mb.subject);
				mimeMessage.setFrom();
			});
		} catch (RuntimeException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	/**
	 * 
	 * @author Administrator
	 *
	 */
	public static final class MessageBean implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		private final String subject;
		private final boolean html;
		private final String text;
		private String to;

		/**
		 * 
		 * @param subject
		 *            主题
		 * @param html
		 *            是否是html
		 * @param text
		 *            内容
		 */
		public MessageBean(String subject, boolean html, String text) {
			super();
			this.subject = subject;
			this.html = html;
			this.text = text;
		}

		public void setTo(String to) {
			this.to = to;
		}

		@Override
		public String toString() {
			return "MessageBean [subject=" + subject + ", html=" + html + ", text=" + text + "]";
		}

	}

	@Override
	public void onApplicationEvent(ContextClosedEvent event) {
		if (!queue.isEmpty()) {
			LOGGER.debug("队列中存在未发送邮件，序列化到本地:{}" , sdfile);
			try {
				SerializationUtils.serialize(queue, sdfile);
			} catch (IOException e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}
}
