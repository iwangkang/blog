//$(document).ready(function(){
//	var login = $("#login").val() == 'true';
//	if(window.sessionStorage && !login){
//		var articleId = "article-"+$("#articleId").val();
//		var item = window.sessionStorage.getItem(articleId);
//		if(!item || item == null){
//			$.post($("#urlPrefix").val()+"/article/hit/"+$("#articleId").val(),{},function(data){
//				if(data.success){
//					window.sessionStorage.setItem(articleId,"1");
//				}
//			})
//		}
//	};
//})
$.post($("#urlPrefix").val()+"/article/hit/"+$("#articleId").val(),{},function(data){
				if(data.success){
					window.sessionStorage.setItem(articleId,"1");
				}
			})

