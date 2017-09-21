var localStorageSupport;
function preview() {
		var page = {"tpl":editor.getValue()};
		page.tpls = fragments;
		var space = $("#spaceSelect").val();
		if(space != null && $.trim(space) != ''){
			page.space = {"id":space}
		}
		var id = $("#pageId").val();
		if(id != null && $.trim(id) != ''){
			page.id = id;
		}
		page.name="test";
		page.description="";
		if($.trim($("#alias").val()) != ''){
			page.alias = $.trim($("#alias").val());
		}
		page.allowComment = $("#allowComment").prop("checked");
		$.ajax({
			type : "post",
			url : basePath + '/mgr/template/page/preview',
			data : JSON.stringify(page),
			dataType : "json",
			contentType : 'application/json',
			success : function(data){
				if (data.success) {
					var url = data.data;
					if(url.hasPathVariable){
						bootbox.dialog({
							title : '预览地址',
							message : '预览路径为<p><b>'+url.url+'</b></p><p>该地址中包含可变参数，请自行访问</p>',
							buttons : {
								success : {
									label : "确定",
									className : "btn-success"
								}
							}
						});
					} else {
						var win = window.open(url.url,
							'_blank');
						win.focus();
					}
				} else {
					showError(data);
				}
			},
			complete:function(){
			}
		});
	}
	 var saveFlag = false;
	function save() {
		var page = {"target":$("#target").val(),"tpl":editor.getValue()};
		page.tpls = fragments;
		var space = $("#spaceSelect").val();
		if(space != ''){
			page.space = {"id":space}
		}
		var url = basePath + '/mgr/template/page/create';
		var id = $("#pageId").val();
		if(id != null && $.trim(id) != ''){
			page.id = id;
			url = basePath + '/mgr/template/page/update';
		}
		if($.trim($("#alias").val()) != ''){
			page.alias = $.trim($("#alias").val());
		}
		page.name=$("#name").val();
		page.description=$("#description").val();
		page.allowComment = $("#allowComment").prop("checked");
		saveFlag = true;
		$.ajax({
			type : "post",
			data : JSON.stringify(page),
			url:url,
			dataType : "json",
			contentType : 'application/json',
			success : function(data){
				if (data.success) {
					removeLocal(key);
					bootbox.alert(data.message);
					setTimeout(function(){
						window.location.href = basePath + '/mgr/template/page/index';
					}, 500);
				} else {
					showError(data);
					saveFlag = false;
				}
			},
			complete:function(){
			
			}
		});
	}
	var key="page_";
	$(document).ready(function(){
		var id = $("#pageId").val();
		if(id != null && $.trim(id) != ''){
			//update
			key=key+id;
		}
		var stored = getFromLocal(key);
		if(stored != null){
			editor.setValue(stored);
		}
		 setInterval(function(){
			 if(!saveFlag){
				 var tpl = $.trim(editor.getValue());
				 if(tpl != ''){
					 storeInLocal(key,tpl);
				 }
			 }
		 }, 5000);
	});
