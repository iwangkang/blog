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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.TokenGroup;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.CollectionUtils;

import me.wangkang.blog.core.dao.ArticleDao;
import me.wangkang.blog.core.dao.TagDao;
import me.wangkang.blog.core.entity.Article;
import me.wangkang.blog.core.entity.Space;
import me.wangkang.blog.core.entity.Tag;
import me.wangkang.blog.core.entity.Article.ArticleFrom;
import me.wangkang.blog.core.evt.ArticleIndexRebuildEvent;
import me.wangkang.blog.core.exception.SystemException;
import me.wangkang.blog.core.pageparam.ArticleQueryParam;
import me.wangkang.blog.core.pageparam.PageResult;
import me.wangkang.blog.util.FileUtils;

/**
 * 文章索引
 * <p>
 * <b>所有的索引写操作都将被放到队列中，然后依次执行，比如新增一篇文章，写文章索引的操作将会被放到最后，因此在执行到该操作之前，该篇文章无法被搜索到</b>
 * </p>
 * 
 * @author Administrator
 *
 */
public abstract class ArticleIndexer implements InitializingBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(ArticleIndexer.class);

	private static final String ID = "id";
	private static final String TITLE = "title";
	private static final String CONTENT = "content";
	private static final String SPACE_ID = "spaceId";
	private static final String PRIVATE = "private";
	private static final String STATUS = "status";
	private static final String FROM = "from";
	private static final String LEVEL = "level";
	private static final String HITS = "hits";
	private static final String PUB_DATE = "pubDate";
	private static final String TAG = "tag";
	private static final String LOCKED = "locked";
	private static final String ALIAS = "alias";
	private static final String SUMMARY = "summary";
	private static final String LASTMODIFYDATE = "lastModifyDate";

	protected Analyzer analyzer;
	private final ControlledRealTimeReopenThread<IndexSearcher> reopenThread;
	private final ReferenceManager<IndexSearcher> searcherManager;
	private final Directory dir;
	private final IndexWriter oriWriter;

	private Formatter titleFormatter;
	private Formatter tagFormatter;
	private Formatter summaryFormatter;

	private Map<String, Float> boostMap = new HashMap<>();
	private Map<String, Float> qboostMap = new HashMap<>();

	/**
	 * 最大查询数量
	 */
	private static final int MAX_RESULTS = 1000;
	/**
	 * @since 5.5.5 当重建所有索引时，每次从数据库中抓取并且索引的数目
	 */
	private static final int DEFAULT_PAGE_SIZE = 100;

	@Autowired(required = false)
	private ArticleContentHandler articleContentHandler;

	@Autowired
	private ArticleDao articleDao;
	@Autowired
	private TagDao tagDao;
	@Autowired
	private PlatformTransactionManager platformTransactionManager;

	private static final Path INDEX_DIR = FileUtils.HOME_DIR.resolve("blog/index");

	private boolean useRAMDirectory = false;

	private int pageSize;

	static {
		FileUtils.forceMkdir(INDEX_DIR);
	}

	private long gen;

	private ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MICROSECONDS,
			new LinkedBlockingQueue<>()) {

		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			super.afterExecute(r, t);
			if (t == null && r instanceof Future) {
				try {
					((Future<?>) r).get();
				} catch (CancellationException ce) {
					t = ce;
				} catch (ExecutionException ee) {
					t = ee.getCause();
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt(); // ignore/reset
				}
			}
			if (t != null) {
				LOGGER.error(t.getMessage(), t);
			}
		}

	};

	/**
	 * 构造器
	 * 
	 * @param analyzer
	 *            分析器
	 * @throws IOException
	 *             索引目录打开失败等
	 * 
	 */
	public ArticleIndexer(Analyzer analyzer) throws IOException {
		this.dir = useRAMDirectory ? new RAMDirectory() : FSDirectory.open(INDEX_DIR);
		this.analyzer = analyzer;
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		config.setOpenMode(OpenMode.CREATE_OR_APPEND);
		try {
			oriWriter = new IndexWriter(dir, config);
			oriWriter.commit();
		} catch (IOException e) {
			throw new SystemException(e.getMessage(), e);
		}
		searcherManager = new SearcherManager(oriWriter, new SearcherFactory());
		reopenThread = new ControlledRealTimeReopenThread<>(oriWriter, searcherManager, 0.5, 0.01);
		reopenThread.start();
	}

	@EventListener
	public void handleCloseEvent(ContextClosedEvent event) {
		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
		}
		try {
			reopenThread.close();
			oriWriter.close();
			dir.close();
			searcherManager.close();
		} catch (AlreadyClosedException | IOException e) {
			LOGGER.warn(e.getMessage(), e);
		}
	}

	protected Document buildDocument(Article article) {
		Document doc = new Document();
		doc.add(new StringField(ID, article.getId().toString(), Field.Store.YES));
		doc.add(new TextField(TITLE, article.getTitle(), Field.Store.YES));
		doc.add(new TextField(SUMMARY, clean(article.getSummary()), Field.Store.YES));
		doc.add(new TextField(CONTENT, cleanContent(article), Field.Store.YES));
		Set<Tag> tags = article.getTags();
		if (!CollectionUtils.isEmpty(tags)) {
			for (Tag tag : tags) {
				doc.add(new TextField(TAG, tag.getName().toLowerCase(), Field.Store.YES));
			}
		}
		doc.add(new StringField(SPACE_ID, article.getSpace().getId().toString(), Field.Store.NO));
		doc.add(new StringField(PRIVATE, article.isPrivate().toString(), Field.Store.NO));
		doc.add(new StringField(STATUS, article.getStatus().name().toLowerCase(), Field.Store.NO));
		doc.add(new StringField(FROM, article.getFrom().name().toLowerCase(), Field.Store.NO));
		doc.add(new StringField(LOCKED, article.hasLock() ? "true" : "false", Field.Store.NO));
		if (article.getAlias() != null) {
			doc.add(new TextField(ALIAS, article.getAlias(), Field.Store.YES));
		}
		Integer level = article.getLevel();
		doc.add(new NumericDocValuesField(LEVEL, level == null ? -1 : level));
		doc.add(new NumericDocValuesField(HITS, article.getHits()));

		String pubDateStr = timeToString(article.getPubDate());
		BytesRef pubDate = new BytesRef(pubDateStr);
		doc.add(new SortedDocValuesField(PUB_DATE, pubDate));
		doc.add(new StringField(PUB_DATE, pubDateStr, Field.Store.NO));

		Timestamp lastModifyDate = article.getLastModifyDate();
		if (lastModifyDate != null) {
			doc.add(new SortedDocValuesField(LASTMODIFYDATE, new BytesRef(timeToString(lastModifyDate))));
		}
		doc.add(new SortedDocValuesField(ID, new BytesRef(article.getId().toString())));
		return doc;
	}

	private String timeToString(Date date) {
		return DateTools.timeToString(date.getTime(), Resolution.MILLISECOND);
	}

	/**
	 * 增加|更新文章索引，如果文章索引存在，则先删除后增加索引
	 * 
	 * @param id
	 *            要增加|更新索引的文章id
	 */
	public synchronized void addOrUpdateDocument(Integer... ids) {
		executor.submit(() -> {
			if (ids == null || ids.length == 0) {
				return null;
			}
			articleDao.selectByIds(Arrays.asList(ids)).stream().filter(Article::isPublished).forEach(art -> {
				try {
					doDeleteDocument(art.getId());
					gen = oriWriter.addDocument(buildDocument(art));
				} catch (IOException e) {
					throw new SystemException(e.getMessage(), e);
				}
			});
			return null;
		});
	}

	/**
	 * 删除索引
	 * 
	 * @param id
	 *            文章id
	 */
	public synchronized void deleteDocument(Integer... ids) {
		executor.submit(() -> {
			for (Integer id : ids) {
				doDeleteDocument(id);
			}
			return null;
		});
	}

	private void doDeleteDocument(Integer id) throws IOException {
		Term term = new Term(ID, id.toString());
		gen = oriWriter.deleteDocuments(term);
	}

	protected void waitForGen() {
		try {
			reopenThread.waitForGeneration(gen);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SystemException(e.getMessage(), e);
		}
	}

	/**
	 * 查询文章
	 * 
	 * @param param
	 *            查询参数
	 * @return 分页内容
	 */
	public PageResult<Article> query(ArticleQueryParam param) {
		IndexSearcher searcher = null;
		try {
			// waitForGen();
			searcher = searcherManager.acquire();
			Sort sort = buildSort(param);

			Builder builder = new Builder();
			Space space = param.getSpace();
			if (space != null) {
				Query query = new TermQuery(new Term(SPACE_ID, space.getId().toString()));
				builder.add(query, Occur.MUST);
			}
			Date begin = param.getBegin();
			Date end = param.getEnd();
			boolean dateRangeQuery = begin != null && end != null;
			if (dateRangeQuery) {
				TermRangeQuery query = new TermRangeQuery(PUB_DATE, new Term(PUB_DATE, timeToString(begin)).bytes(),
						new Term(PUB_DATE, timeToString(end)).bytes(), true, true);
				builder.add(query, Occur.MUST);
			}
			if (!param.isQueryPrivate()) {
				builder.add(new TermQuery(new Term(PRIVATE, "false")), Occur.MUST);
				builder.add(new TermQuery(new Term(LOCKED, "false")), Occur.MUST);
			}
			ArticleFrom from = param.getFrom();
			if (from != null) {
				Query query = new TermQuery(new Term(FROM, from.name().toLowerCase()));
				builder.add(query, Occur.MUST);
			}
			if (param.getTag() != null) {
				builder.add(new TermQuery(new Term(TAG, param.getTag())), Occur.MUST);
			}

			if (!CollectionUtils.isEmpty(param.getSpaceIds())) {
				for (Integer id : param.getSpaceIds()) {
					builder.add(new TermQuery(new Term(SPACE_ID, String.valueOf(id))), Occur.SHOULD);
				}
			}

			Optional<Query> optionalMultiFieldQuery = param.hasQuery() ? buildMultiFieldQuery(param.getQuery())
					: Optional.empty();
			optionalMultiFieldQuery.ifPresent(query -> builder.add(query, Occur.MUST));
			Query query = builder.build();

			TopDocs tds = searcher.search(query, MAX_RESULTS, sort);
			int total = tds.totalHits;
			int offset = param.getOffset();
			Map<Integer, Document> datas = new LinkedHashMap<>();
			if (offset < total) {
				ScoreDoc[] docs = tds.scoreDocs;
				int last = offset + param.getPageSize();
				for (int i = offset; i < Math.min(Math.min(last, total), MAX_RESULTS); i++) {
					int docId = docs[i].doc;
					Document doc = searcher.doc(docId);
					datas.put(Integer.parseInt(doc.get(ID)), doc);
				}
			}
			List<Article> articles = selectByIds(datas.keySet());
			if (param.isHighlight() && optionalMultiFieldQuery.isPresent()) {
				for (Article article : articles) {
					doHightlight(article, datas.get(article.getId()), optionalMultiFieldQuery.get());
					article.setContent(null);
				}
			}
			return new PageResult<>(param, Math.min(MAX_RESULTS, total), articles);
		} catch (IOException e) {
			throw new SystemException(e.getMessage(), e);
		} finally {
			try {
				if (searcher != null) {
					searcherManager.release(searcher);
					searcher = null;
				}
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}

	private List<Article> selectByIds(Collection<Integer> ids) {
		if (CollectionUtils.isEmpty(ids)) {
			return Collections.emptyList();
		}
		List<Article> articles = articleDao.selectPageByIds(ids);
		if (articles.isEmpty()) {
			return Collections.emptyList();
		}
		Map<Integer, Article> map = articles.stream().collect(Collectors.toMap(Article::getId, article -> article));
		return ids.stream().map(map::get).filter(Objects::nonNull).collect(Collectors.toList());
	}

	protected Optional<Query> buildMultiFieldQuery(String query) {
		String escaped = MultiFieldQueryParser.escape(query.trim());
		MultiFieldQueryParser parser = new MultiFieldQueryParser(new String[] { TAG, TITLE, ALIAS, SUMMARY, CONTENT },
				analyzer, qboostMap);
		parser.setAutoGeneratePhraseQueries(true);
		parser.setPhraseSlop(0);
		try {
			return Optional.of(parser.parse(escaped));
		} catch (ParseException e) {
			LOGGER.debug("无法解析输入的查询表达式:" + escaped + ":" + e.getMessage(), e);
			return Optional.empty();
		}
	}

	protected Sort buildSort(ArticleQueryParam param) {
		List<SortField> fields = new ArrayList<>();
		if (!param.isIgnoreLevel()) {
			fields.add(new SortField(LEVEL, Type.INT, true));
		}
		ArticleQueryParam.Sort psort = param.getSort();
		if (psort == null) {
			fields.add(SortField.FIELD_SCORE);
		} else {
			switch (param.getSort()) {
			case HITS:
				fields.add(new SortField(HITS, Type.INT, true));
				break;
			case PUBDATE:
				fields.add(new SortField(PUB_DATE, SortField.Type.STRING, true));
				break;
			case LASTMODIFYDATE:
				fields.add(new SortField(LASTMODIFYDATE, SortField.Type.STRING, true));
				fields.add(new SortField(PUB_DATE, SortField.Type.STRING, true));
				break;
			default:
				break;
			}
			fields.add(new SortField(ID, SortField.Type.STRING, true));
		}
		return new Sort(fields.toArray(new SortField[] {}));
	}

	/**
	 * 高亮显示
	 * 
	 * @param article
	 *            文章
	 * @param content
	 *            文章内容
	 * @param query
	 */
	protected void doHightlight(Article article, Document doc, Query query) {

		String content = doc.get(CONTENT);
		String summary = doc.get(SUMMARY);
		String title = doc.get(TITLE);
		String[] tags = doc.getValues(TAG);

		getHightlight(new Highlighter(titleFormatter, new QueryScorer(query)), TITLE, title)
				.ifPresent(article::setTitle);
		Optional<String> summaryHl = getHightlight(new Highlighter(summaryFormatter, new QueryScorer(query)), SUMMARY,
				summary);
		if (summaryHl.isPresent()) {
			article.setSummary(summaryHl.get());
		} else {
			getHightlight(new Highlighter(summaryFormatter, new QueryScorer(query)), CONTENT, content)
					.ifPresent(article::setSummary);
		}
		if (tags != null && tags.length > 0) {
			for (String tag : tags) {
				Optional<Tag> optionalTag = article.getTag(tag);
				if (optionalTag.isPresent()) {
					Tag _tag = optionalTag.get();
					getHightlight(new Highlighter(tagFormatter, new QueryScorer(query)), TAG, _tag.getName())
							.ifPresent(_tag::setName);
				}
			}
		}

	}

	private Optional<String> getHightlight(Highlighter highlighter, String fieldName, String text) {
		try {
			return Optional.ofNullable(highlighter.getBestFragment(analyzer, fieldName, text));
		} catch (Exception e) {
			throw new SystemException(e.getMessage(), e);
		}
	}

	private String clean(String content) {
		// 只需要纯文字的内容
		return Jsoup.clean(content, Whitelist.none());
	}

	private String cleanContent(Article article) {
		if (articleContentHandler != null) {
			articleContentHandler.handle(article);
		}
		return clean(article.getContent());
	}

	/**
	 * 删除标签
	 * 
	 * @param tags
	 *            要删除的标签名
	 */
	public synchronized void removeTags(String... tags) {
		executor.submit(() -> {
			doRemoveTags(tags);
			return null;
		});
	}

	/**
	 * 增加标签
	 * 
	 * @param tags
	 *            标签名
	 */
	public synchronized void addTags(String... tags) {
		executor.submit(() -> {
			doAddTags(tags);
			return null;
		});
	}

	/**
	 * 重建索引
	 * 
	 * @throws IOException
	 * 
	 */
	public synchronized void rebuildIndex() {
		try {
			gen = oriWriter.deleteAll();
		} catch (IOException e) {
			throw new SystemException(e.getMessage(), e);
		}
		executor.submit(() -> {
			long start = System.currentTimeMillis();
			Transactions.executeInReadOnlyTransaction(platformTransactionManager, status -> {
				// return articleDao.selectPublished(null);
				int offset = 0;
				int limit = getPageSize();
				List<Article> articles;
				List<Document> documents = new ArrayList<>();
				while (!(articles = articleDao.selectPublishedPage(offset, limit)).isEmpty()) {
					offset += limit;
					for (Article article : articles) {
						documents.add(buildDocument(article));
					}
					try {
						gen = oriWriter.addDocuments(documents);
					} catch (IOException e) {
						throw new SystemException(e.getMessage(), e);
					}
					documents.clear();
					articles = null;
				}
				return null;
			});
			LOGGER.debug("重建索引花费了：" + (System.currentTimeMillis() - start) + "ms");
			return null;
		});
	}

	/**
	 * 应该只有在transaction完成之后才会被触发
	 * 
	 * @param event
	 */
	@EventListener
	public void handleArticleIndexRebuildEvent(ArticleIndexRebuildEvent event) {
		rebuildIndex();
	}

	protected abstract void doRemoveTags(String... tags);

	protected abstract void doAddTags(String... tags);

	@Override
	public void afterPropertiesSet() throws Exception {
		if (titleFormatter == null) {
			titleFormatter = new DefaultFormatter("lucene-highlight-title");
		}
		if (tagFormatter == null) {
			tagFormatter = new DefaultFormatter("lucene-highlight-tag");
		}
		if (summaryFormatter == null) {
			summaryFormatter = new DefaultFormatter("lucene-highlight-summary");
		}
		qboostMap.put(TAG, boostMap.getOrDefault(TAG, 20F));
		qboostMap.put(ALIAS, boostMap.getOrDefault(ALIAS, 10F));
		qboostMap.put(TITLE, boostMap.getOrDefault(TITLE, 7F));
		qboostMap.put(SUMMARY, boostMap.getOrDefault(SUMMARY, 3F));
		qboostMap.put(CONTENT, boostMap.getOrDefault(CONTENT, 1F));
		// 新增标签
		addTags(tagDao.selectAll().stream().map(Tag::getName).toArray(i -> new String[i]));
	}

	/**
	 * <p>
	 * <b>仅供定时任务调用！！！</b>
	 * </p>
	 */
	public synchronized void commit() {
		executor.submit(() -> {
			oriWriter.commit();
			return null;
		});
	}

	private static final class DefaultFormatter implements Formatter {
		private String classes;

		private DefaultFormatter(String classes) {
			this.classes = classes;
		}

		@Override
		public String highlightTerm(String originalText, TokenGroup tokenGroup) {
			return tokenGroup.getTotalScore() <= 0 ? originalText
					: new StringBuilder("<b class=\"").append(classes).append("\">").append(originalText).append("</b>")
							.toString();
		}
	}

	public void setBoostMap(Map<String, Float> boostMap) {
		this.boostMap = boostMap;
	}

	public void setTitleFormatter(Formatter titleFormatter) {
		this.titleFormatter = titleFormatter;
	}

	public void setTagFormatter(Formatter tagFormatter) {
		this.tagFormatter = tagFormatter;
	}

	public void setSummaryFormatter(Formatter summaryFormatter) {
		this.summaryFormatter = summaryFormatter;
	}

	public void setUseRAMDirectory(boolean useRAMDirectory) {
		this.useRAMDirectory = useRAMDirectory;
	}

	private int getPageSize() {
		return pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}
}
