package me.wangkang.blog.web.template;

public class ParseContextHolder {

	private static final ThreadLocal<ParseContext> CONTEXT_LOCAL = ThreadLocal.withInitial(ParseContext::new);

	private ParseContextHolder() {
		super();
	}

	public static ParseContext getContext() {
		return CONTEXT_LOCAL.get();
	}

	public static void remove() {
		CONTEXT_LOCAL.remove();
	}

}
