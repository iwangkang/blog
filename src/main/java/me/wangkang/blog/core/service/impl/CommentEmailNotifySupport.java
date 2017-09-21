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
package me.wangkang.blog.core.service.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import me.wangkang.blog.core.config.Constants;
import me.wangkang.blog.core.config.UrlHelper;
import me.wangkang.blog.core.entity.Comment;
import me.wangkang.blog.core.evt.CommentEvent;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.message.Messages;
import me.wangkang.blog.support.mail.MailSender;
import me.wangkang.blog.support.mail.MailSender.MessageBean;
import me.wangkang.blog.util.FileUtils;
import me.wangkang.blog.util.Resources;
import me.wangkang.blog.util.SerializationUtils;

/**
 * 用来向管理员发送评论|回复通知邮件
 * <p>
 * <strong>删除评论不会对邮件的发送造成影响，即如果发送队列中或者待发送列表中的一条评论已经被删除，那么它将仍然被发送</strong>
 * </p>
 * 
 * @author Administrator
 *
 */
public class CommentEmailNotifySupport implements InitializingBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(CommentEmailNotifySupport.class);
	private ConcurrentLinkedQueue<Comment> toProcesses = new ConcurrentLinkedQueue<>();
	private List<Comment> toSend = Collections.synchronizedList(new ArrayList<>());
	private MailTemplateEngine mailTemplateEngine = new MailTemplateEngine();
	private Resource mailTemplateResource;
	private String mailTemplate;
	private String mailSubject;

	private Path toSendSdfile = Constants.DAT_DIR.resolve("comment-toSendSdfile.dat");
	private Path toProcessesSdfile = Constants.DAT_DIR.resolve("comment-toProcessesSdfile.dat");

	/**
	 * 如果待发送列表中有10或以上的评论，立即发送邮件
	 */
	private static final Integer MESSAGE_TIP_COUNT = 10;

	private int messageTipCount = MESSAGE_TIP_COUNT;

	@Autowired
	private MailSender mailSender;
	@Autowired
	private UrlHelper urlHelper;
	@Autowired
	private Messages messages;

	private void sendMail(List<Comment> comments, String to) {
		Context context = new Context();
		context.setVariable("urls", urlHelper.getUrls());
		context.setVariable("comments", comments);
		context.setVariable("messages", messages);
		String text = mailTemplateEngine.process(mailTemplate, context);
		MessageBean mb = new MessageBean(mailSubject, true, text);
		if (to != null) {
			mb.setTo(to);
		}
		mailSender.send(mb);
	}

	private final class MailTemplateEngine extends TemplateEngine {
		public MailTemplateEngine() {
			setTemplateResolver(new StringTemplateResolver());
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (mailSubject == null) {
			throw new SystemException("邮件标题不能为空");
		}

		if (mailTemplateResource == null) {
			mailTemplateResource = new ClassPathResource("resources/page/defaultMailTemplate.html");
		}

		mailTemplate = Resources.readResourceToString(mailTemplateResource);

		if (messageTipCount <= 0) {
			messageTipCount = MESSAGE_TIP_COUNT;
		}

		if (FileUtils.exists(toSendSdfile)) {
			try {
				this.toSend = SerializationUtils.deserialize(toSendSdfile);
			} catch (Exception e) {
				LOGGER.warn("载入文件：" + toSendSdfile + "失败:" + e.getMessage(), e);
			} finally {
				if (!FileUtils.deleteQuietly(toSendSdfile)) {
					LOGGER.warn("删除文件:{}失败，这会导致邮件重复发送", toSendSdfile);
				}
			}
		}

		if (FileUtils.exists(toProcessesSdfile)) {
			try {
				this.toProcesses = SerializationUtils.deserialize(toProcessesSdfile);
			} catch (Exception e) {
				LOGGER.warn("载入文件：" + toProcessesSdfile + "失败:" + e.getMessage(), e);
			} finally {
				if (!FileUtils.deleteQuietly(toProcessesSdfile)) {
					LOGGER.warn("删除文件:{}失败，这会导致邮件重复发送", toProcessesSdfile);
				}
			}
		}
	}

	public void forceSend() {
		synchronized (toSend) {
			if (!toSend.isEmpty()) {
				LOGGER.debug("待发送列表不为空，将会发送邮件，无论发送列表是否达到{}", messageTipCount);
				sendMail(toSend, null);
				toSend.clear();
			}
		}
	}

	public void processToSend() {
		synchronized (toSend) {
			int size = toSend.size();
			for (Iterator<Comment> iterator = toProcesses.iterator(); iterator.hasNext();) {
				Comment toProcess = iterator.next();
				toSend.add(toProcess);
				size++;
				iterator.remove();
				if (size >= messageTipCount) {
					LOGGER.debug("发送列表尺寸达到{}立即发送邮件通知", messageTipCount);
					sendMail(toSend, null);
					toSend.clear();
					break;
				}
			}
		}
	}

	public void setMailTemplateResource(Resource mailTemplateResource) {
		this.mailTemplateResource = mailTemplateResource;
	}

	public void setMailSubject(String mailSubject) {
		this.mailSubject = mailSubject;
	}

	public void setMessageTipCount(int messageTipCount) {
		this.messageTipCount = messageTipCount;
	}

	@Async
	@TransactionalEventListener
	public void handleCommentEvent(CommentEvent evt) {
		Comment comment = evt.getComment();
		Comment parent = comment.getParent();
		// 如果在用户登录的情况下评论，一律不发送邮件
		// 如果回复了管理员
		if (!comment.getAdmin() && (parent == null || parent.getAdmin())) {
			toProcesses.add(comment);
		}
		// 如果父评论不是管理员的评论
		// 如果回复是管理员
		if (parent != null && parent.getEmail() != null && !parent.getAdmin() && comment.getAdmin()) {
			// 直接邮件通知被回复对象
			sendMail(Arrays.asList(comment), comment.getParent().getEmail());
		}
	}

	@EventListener
	public void handleContextClosedEvent(ContextClosedEvent event) {
		try {
			if (!toSend.isEmpty()) {
				SerializationUtils.serialize(toSend, toSendSdfile);
			}
			if (!toProcesses.isEmpty()) {
				SerializationUtils.serialize(toProcesses, toProcessesSdfile);
			}
		} catch (IOException e) {
			LOGGER.warn(e.getMessage(), e);
		}
	}

}
