$(function() {
	loadUncheckCommentCount();
});

function loadUncheckCommentCount() {
	$.get(rootPath + '/mgr/comment/uncheckCount', {}, function(data) {
		if (data.success) {
			var count = data.data;

			$("#uncheck-comment-count").html(count);

			if (count > 0) {
				$("#uncheck-comment-count").show();
			} else {
				$("#uncheck-comment-count").hide();
			}

		}
	})
}